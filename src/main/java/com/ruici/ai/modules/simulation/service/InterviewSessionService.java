package com.ruici.ai.modules.simulation.service;

import com.ruici.ai.common.constant.CommonConstants.ScenarioDefaults;
import com.ruici.ai.common.ai.LlmProviderRegistry;
import com.ruici.ai.common.exception.BusinessException;
import com.ruici.ai.common.exception.ErrorCode;
import com.ruici.ai.common.model.AsyncTaskStatus;
import com.ruici.ai.infrastructure.redis.InterviewSessionCache;
import com.ruici.ai.infrastructure.redis.InterviewSessionCache.CachedSession;
import com.ruici.ai.modules.document.service.ResumePersistenceService;
import com.ruici.ai.modules.simulation.listener.EvaluateStreamProducer;
import com.ruici.ai.modules.simulation.model.CreateInterviewRequest;
import com.ruici.ai.modules.simulation.model.HistoricalQuestion;
import com.ruici.ai.modules.simulation.model.InterviewAnswerEntity;
import com.ruici.ai.modules.simulation.model.InterviewQuestionDTO;
import com.ruici.ai.modules.simulation.model.InterviewReportDTO;
import com.ruici.ai.modules.simulation.model.InterviewSessionDTO;
import com.ruici.ai.modules.simulation.model.InterviewSessionEntity;
import com.ruici.ai.modules.simulation.model.SimulationDirection;
import com.ruici.ai.modules.simulation.model.SimulationDifficulty;
import com.ruici.ai.modules.simulation.model.SimulationScenarioType;
import com.ruici.ai.modules.simulation.model.SubmitAnswerRequest;
import com.ruici.ai.modules.simulation.model.SubmitAnswerResponse;
import com.ruici.ai.modules.simulation.model.InterviewSessionDTO.SessionStatus;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Service;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * 面试会话管理服务
 * 管理面试会话的生命周期，使用 Redis 缓存会话状态
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class InterviewSessionService {

    private final InterviewQuestionService questionService;
    private final AnswerEvaluationService evaluationService;
    private final InterviewPersistenceService persistenceService;
    private final InterviewSessionCache sessionCache;
    private final ObjectMapper objectMapper;
    private final EvaluateStreamProducer evaluateStreamProducer;
    private final LlmProviderRegistry llmProviderRegistry;
    private final ResumePersistenceService resumePersistenceService;

    /**
     * 创建新的面试会话
     * 注意：如果已有未完成的会话，不会创建新的，而是返回现有会话
     * 前端应该先调用 findUnfinishedSession 检查，或者使用 forceCreate 参数强制创建
     */
    public InterviewSessionDTO createSession(CreateInterviewRequest request) {
        SimulationDirection simulationDirection = request.simulationDirection() != null
            ? request.simulationDirection()
            : SimulationDirection.fromScenarioType(request.scenarioType());
        SimulationScenarioType scenarioType = SimulationScenarioType.fromNullable(simulationDirection.scenarioTypeId());
        SimulationDifficulty simulationDifficulty = request.simulationDifficulty() != null
            ? request.simulationDifficulty()
            : SimulationDifficulty.fromLegacy(request.difficulty());
        String skillId = request.skillId() != null ? request.skillId() : ScenarioDefaults.SKILL_ID;
        boolean basedOnDocument = request.basedOnDocument() != null
            ? request.basedOnDocument()
            : request.resumeId() != null || (request.resumeText() != null && !request.resumeText().isBlank());
        Long effectiveResumeId = basedOnDocument ? request.resumeId() : null;
        String effectiveResumeText = basedOnDocument
            ? resolveResumeText(request.resumeId(), request.resumeText())
            : null;
        int effectiveQuestionCount = request.questionCount() != null
            ? request.questionCount()
            : ScenarioDefaults.QUESTION_COUNT;
        if (basedOnDocument && (effectiveResumeText == null || effectiveResumeText.isBlank())) {
            throw new BusinessException(
                ErrorCode.BAD_REQUEST,
                "基于文档的情景模拟必须提供可用的文档内容"
            );
        }
        validateResumeTextUsable(effectiveResumeText);

        // 如果指定了resumeId且未强制创建，检查是否有未完成的会话
        if (effectiveResumeId != null && !Boolean.TRUE.equals(request.forceCreate())) {
            Optional<InterviewSessionDTO> unfinishedOpt = findUnfinishedSession(
                effectiveResumeId,
                scenarioType.id(),
                skillId
            );
            if (unfinishedOpt.isPresent()) {
                log.info("检测到未完成的面试会话，返回现有会话: resumeId={}, sessionId={}",
                    effectiveResumeId, unfinishedOpt.get().sessionId());
                return unfinishedOpt.get();
            }
        }

        String sessionId = UUID.randomUUID().toString().replace("-", "").substring(0, 16);
        String difficulty = request.difficulty() != null
            ? request.difficulty()
            : simulationDifficulty.toLegacyDifficulty();

        log.info("创建新面试会话: {}, simulationDirection={}, scenarioType={}, skill={}, difficulty={}, simulationDifficulty={}, questionCount={}, resumeId={}, basedOnDocument={}",
            sessionId, simulationDirection.name(), scenarioType.id(), skillId, difficulty, simulationDifficulty.name(),
            effectiveQuestionCount, effectiveResumeId, basedOnDocument);

        // 获取历史问题（通用模式按 skillId 查询，有简历时按 resumeId + skillId 精确匹配）
        List<HistoricalQuestion> historicalQuestions =
            persistenceService.getHistoricalQuestions(scenarioType.id(), skillId, effectiveResumeId);

        // 获取 LLM 客户端
        ChatClient chatClient = llmProviderRegistry.getChatClientOrDefault(request.llmProvider());

        // 基于 Skill 生成面试问题
        List<InterviewQuestionDTO> questions = questionService.generateQuestionsBySkill(
            chatClient,
            scenarioType,
            skillId,
            difficulty,
            effectiveResumeText,
            effectiveQuestionCount,
            historicalQuestions,
            request.customCategories(),
            request.jdText()
        );

        // 保存到 Redis 缓存
        int totalMainQuestions = countMainQuestions(questions);
        sessionCache.saveSession(
            sessionId,
            simulationDirection.name(),
            scenarioType.id(),
            skillId,
            difficulty,
            simulationDifficulty.name(),
            effectiveResumeText != null ? effectiveResumeText : "",
            effectiveResumeId,
            basedOnDocument,
            questions,
            0,
            SessionStatus.CREATED
        );

        // 保存到数据库
        try {
            persistenceService.saveSession(
                sessionId,
                scenarioType.id(),
                effectiveResumeId,
                totalMainQuestions,
                questions,
                request.llmProvider(),
                skillId,
                difficulty,
                simulationDirection.name(),
                simulationDifficulty.name(),
                basedOnDocument
            );
        } catch (Exception e) {
            sessionCache.deleteSession(sessionId);
            log.error("保存情景模拟会话到数据库失败，已回滚缓存: sessionId={}", sessionId, e);
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "创建情景模拟会话失败，请稍后重试", e);
        }

        return new InterviewSessionDTO(
            sessionId,
            simulationDirection.name(),
            scenarioType.id(),
            simulationDifficulty.name(),
            effectiveResumeText != null ? effectiveResumeText : "",
            effectiveResumeId,
            basedOnDocument,
            skillId,
            totalMainQuestions,
            0,
            questions,
            SessionStatus.CREATED
        );
    }

    /**
     * 获取会话信息（优先从缓存获取，缓存未命中则从数据库恢复）
     */
    public InterviewSessionDTO getSession(String sessionId) {
        // 1. 尝试从 Redis 缓存获取
        Optional<CachedSession> cachedOpt = sessionCache.getSession(sessionId);
        if (cachedOpt.isPresent()) {
            return toDTO(cachedOpt.get());
        }

        // 2. 缓存未命中，从数据库恢复
        CachedSession restoredSession = restoreSessionFromDatabase(sessionId);
        if (restoredSession == null) {
            throw new BusinessException(ErrorCode.INTERVIEW_SESSION_NOT_FOUND);
        }

        return toDTO(restoredSession);
    }

    /**
     * 查找并恢复未完成的面试会话
     */
    public Optional<InterviewSessionDTO> findUnfinishedSession(Long resumeId) {
        try {
            // 1. 先从 Redis 缓存查找
            Optional<String> cachedSessionIdOpt = sessionCache.findUnfinishedSessionId(resumeId);
            if (cachedSessionIdOpt.isPresent()) {
                String sessionId = cachedSessionIdOpt.get();
                Optional<CachedSession> cachedOpt = sessionCache.getSession(sessionId);
                if (cachedOpt.isPresent()) {
                    log.debug("从 Redis 缓存找到未完成会话: resumeId={}, sessionId={}", resumeId, sessionId);
                    return Optional.of(toDTO(cachedOpt.get()));
                }
            }

            // 2. 缓存未命中，从数据库查找
            Optional<InterviewSessionEntity> entityOpt = persistenceService.findUnfinishedSession(resumeId);
            if (entityOpt.isEmpty()) {
                return Optional.empty();
            }

            InterviewSessionEntity entity = entityOpt.get();
            CachedSession restoredSession = restoreSessionFromEntity(entity);
            if (restoredSession != null) {
                return Optional.of(toDTO(restoredSession));
            }
        } catch (Exception e) {
            log.error("恢复未完成会话失败: {}", e.getMessage(), e);
        }
        return Optional.empty();
    }

    /**
     * 按文档 + 场景 + 模板恢复未完成会话。
     */
    public Optional<InterviewSessionDTO> findUnfinishedSession(Long resumeId, String scenarioType, String skillId) {
        try {
            Optional<String> cachedSessionIdOpt = sessionCache.findUnfinishedSessionId(resumeId, scenarioType, skillId);
            if (cachedSessionIdOpt.isPresent()) {
                String sessionId = cachedSessionIdOpt.get();
                Optional<CachedSession> cachedOpt = sessionCache.getSession(sessionId);
                if (cachedOpt.isPresent()) {
                    log.debug("从 Redis 缓存找到 scoped 未完成会话: resumeId={}, scenarioType={}, skillId={}, sessionId={}",
                        resumeId, scenarioType, skillId, sessionId);
                    return Optional.of(toDTO(cachedOpt.get()));
                }
            }

            Optional<InterviewSessionEntity> entityOpt = persistenceService.findUnfinishedSession(resumeId, scenarioType, skillId);
            if (entityOpt.isEmpty()) {
                return Optional.empty();
            }

            CachedSession restoredSession = restoreSessionFromEntity(entityOpt.get());
            if (restoredSession != null) {
                return Optional.of(toDTO(restoredSession));
            }
        } catch (Exception e) {
            log.error("恢复 scoped 未完成会话失败: resumeId={}, scenarioType={}, skillId={}",
                resumeId, scenarioType, skillId, e);
        }
        return Optional.empty();
    }

    /**
     * 查找并恢复未完成的面试会话，如果不存在则抛出异常
     */
    public InterviewSessionDTO findUnfinishedSessionOrThrow(Long resumeId) {
        return findUnfinishedSession(resumeId)
            .orElseThrow(() -> new BusinessException(ErrorCode.INTERVIEW_SESSION_NOT_FOUND, "未找到未完成的面试会话"));
    }

    /**
     * 从数据库恢复会话并缓存到 Redis
     */
    private CachedSession restoreSessionFromDatabase(String sessionId) {
        try {
            Optional<InterviewSessionEntity> entityOpt = persistenceService.findBySessionId(sessionId);
            return entityOpt.map(this::restoreSessionFromEntity).orElse(null);
        } catch (Exception e) {
            log.error("从数据库恢复会话失败: {}", e.getMessage(), e);
            return null;
        }
    }

    /**
     * 从实体恢复会话并缓存到 Redis
     */
    private CachedSession restoreSessionFromEntity(InterviewSessionEntity entity) {
        try {
            // 解析问题列表
            List<InterviewQuestionDTO> questions = objectMapper.readValue(
                entity.getQuestionsJson(),
                new TypeReference<>() {}
            );

            // 恢复已保存的答案
            List<InterviewAnswerEntity> answers = persistenceService.findAnswersBySessionId(entity.getSessionId());
            for (InterviewAnswerEntity answer : answers) {
                int index = answer.getQuestionIndex();
                if (index >= 0 && index < questions.size()) {
                    InterviewQuestionDTO question = questions.get(index);
                    questions.set(index, question.withAnswer(answer.getUserAnswer()));
                }
            }

            SessionStatus status = convertStatus(entity.getStatus());

            // 保存到 Redis 缓存
            Long documentId = entity.getDocumentId();
            String resumeText = entity.getResume() != null ? entity.getResume().getResumeText() : "";
            sessionCache.saveSession(
                entity.getSessionId(),
                entity.getSimulationDirection(),
                SimulationScenarioType.fromNullable(entity.getScenarioType()).id(),
                entity.getSkillId(),
                entity.getDifficulty(),
                entity.getSimulationDifficulty(),
                resumeText,
                documentId,
                entity.getBasedOnDocument(),
                questions,
                entity.getCurrentQuestionIndex(),
                status
            );

            log.info("从数据库恢复会话到 Redis: sessionId={}, currentIndex={}, status={}",
                entity.getSessionId(), entity.getCurrentQuestionIndex(), entity.getStatus());

            // 返回缓存的会话
            return sessionCache.getSession(entity.getSessionId()).orElse(null);
        } catch (Exception e) {
            log.error("恢复会话失败: {}", e.getMessage(), e);
            return null;
        }
    }

    private SessionStatus convertStatus(InterviewSessionEntity.SessionStatus status) {
        return switch (status) {
            case CREATED -> SessionStatus.CREATED;
            case IN_PROGRESS -> SessionStatus.IN_PROGRESS;
            case COMPLETED -> SessionStatus.COMPLETED;
            case EVALUATED -> SessionStatus.EVALUATED;
        };
    }

    /**
     * 获取当前问题的响应（包含完成状态）
     */
    public Map<String, Object> getCurrentQuestionResponse(String sessionId) {
        InterviewQuestionDTO question = getCurrentQuestion(sessionId);
        if (question == null) {
            return Map.of(
                "completed", true,
                "message", "所有问题已回答完毕"
            );
        }
        return Map.of(
            "completed", false,
            "question", question
        );
    }

    /**
     * 获取当前问题
     */
    public InterviewQuestionDTO getCurrentQuestion(String sessionId) {
        CachedSession session = getOrRestoreSession(sessionId);
        List<InterviewQuestionDTO> questions = session.getQuestions(objectMapper);

        if (session.getCurrentIndex() >= questions.size()) {
            return null; // 所有问题已回答完
        }

        // 更新状态为进行中
        if (session.getStatus() == SessionStatus.CREATED) {
            session.setStatus(SessionStatus.IN_PROGRESS);
            sessionCache.updateSessionStatus(sessionId, SessionStatus.IN_PROGRESS);

            // 同步到数据库
            try {
                persistenceService.updateSessionStatus(sessionId,
                    InterviewSessionEntity.SessionStatus.IN_PROGRESS);
            } catch (Exception e) {
                log.warn("更新会话状态失败: {}", e.getMessage());
            }
        }

        return questions.get(session.getCurrentIndex());
    }

    /**
     * 提交答案（并进入下一题）
     * 如果是最后一题，自动触发异步评估
     */
    public SubmitAnswerResponse submitAnswer(SubmitAnswerRequest request) {
        CachedSession session = getOrRestoreSession(request.sessionId());
        List<InterviewQuestionDTO> questions = session.getQuestions(objectMapper);

        int index = request.questionIndex();
        if (index < 0 || index >= questions.size()) {
            throw new BusinessException(ErrorCode.INTERVIEW_QUESTION_NOT_FOUND, "无效的问题索引: " + index);
        }

        // 更新问题答案
        InterviewQuestionDTO question = questions.get(index);
        InterviewQuestionDTO answeredQuestion = question.withAnswer(request.answer());
        questions.set(index, answeredQuestion);

        // 移动到下一题
        int newIndex = index + 1;

        // 检查是否全部完成
        boolean hasNextQuestion = newIndex < questions.size();
        InterviewQuestionDTO nextQuestion = hasNextQuestion ? questions.get(newIndex) : null;

        SessionStatus newStatus = hasNextQuestion ? SessionStatus.IN_PROGRESS : SessionStatus.COMPLETED;

        // 更新 Redis 缓存
        sessionCache.updateQuestions(request.sessionId(), questions);
        sessionCache.updateCurrentIndex(request.sessionId(), newIndex);
        if (newStatus == SessionStatus.COMPLETED) {
            sessionCache.updateSessionStatus(request.sessionId(), SessionStatus.COMPLETED);
        }

        // 保存答案到数据库
        try {
            persistenceService.saveAnswer(
                request.sessionId(), index,
                question.question(), question.category(),
                request.answer(), 0, null  // 分数在报告生成时更新
            );
            persistenceService.updateCurrentQuestionIndex(request.sessionId(), newIndex);
            persistenceService.updateSessionStatus(request.sessionId(),
                newStatus == SessionStatus.COMPLETED
                    ? InterviewSessionEntity.SessionStatus.COMPLETED
                    : InterviewSessionEntity.SessionStatus.IN_PROGRESS);

            // 如果是最后一题，设置评估状态为 PENDING 并触发异步评估
            if (!hasNextQuestion) {
                persistenceService.updateEvaluateStatus(request.sessionId(), AsyncTaskStatus.PENDING, null);
                evaluateStreamProducer.sendEvaluateTask(request.sessionId());
                log.info("会话 {} 已完成所有问题，评估任务已入队", request.sessionId());
            }
        } catch (Exception e) {
            log.warn("保存答案到数据库失败: {}", e.getMessage());
        }

        log.info("会话 {} 提交答案: 问题{}, 剩余{}题",
            request.sessionId(), index, questions.size() - newIndex);

        return new SubmitAnswerResponse(
            hasNextQuestion,
            nextQuestion,
            newIndex,
            countMainQuestions(questions)
        );
    }

    static int countMainQuestions(List<InterviewQuestionDTO> questions) {
        return (int) questions.stream()
            .filter(question -> !question.isFollowUp())
            .count();
    }

    static void validateResumeTextUsable(String resumeText) {
        if (resumeText == null || resumeText.isBlank()) {
            return;
        }

        String normalized = resumeText.trim();
        if (normalized.length() < 80) {
            throw new BusinessException(
                ErrorCode.BAD_REQUEST,
                "当前文档内容过短，无法生成基于简历的模拟题，请更换文档或使用通用提问模式"
            );
        }

        long meaningfulCharCount = normalized.chars()
            .filter(character -> Character.isLetterOrDigit(character) || Character.UnicodeScript.of(character) == Character.UnicodeScript.HAN)
            .count();
        if (meaningfulCharCount < 40) {
            throw new BusinessException(
                ErrorCode.BAD_REQUEST,
                "当前文档内容噪声过多，无法稳定提取简历信息，请更换文档或使用通用提问模式"
            );
        }

        String lowerCaseText = normalized.toLowerCase();
        List<String> resumeSignals = List.of(
            "项目", "经历", "经验", "技能", "教育", "工作", "职责",
            "project", "experience", "skill", "education", "work", "responsibility"
        );
        long signalCount = resumeSignals.stream()
            .filter(lowerCaseText::contains)
            .count();
        if (signalCount == 0) {
            throw new BusinessException(
                ErrorCode.BAD_REQUEST,
                "当前文档不像可用于面试的问题素材，请上传更贴近履历/经历的文档，或使用通用提问模式"
            );
        }
    }

    private String resolveResumeText(Long resumeId, String requestedResumeText) {
        if (requestedResumeText != null && !requestedResumeText.isBlank()) {
            return requestedResumeText;
        }

        if (resumeId == null) {
            return null;
        }

        return resumePersistenceService.findById(resumeId)
            .map(resume -> resume.getResumeText())
            .filter(text -> text != null && !text.isBlank())
            .orElseThrow(() -> new BusinessException(
                ErrorCode.BAD_REQUEST,
                "当前文档缺少可用正文，请重新分析文档后再开启基于文档的情景模拟"
            ));
    }

    /**
     * 暂存答案（不进入下一题）
     */
    public void saveAnswer(SubmitAnswerRequest request) {
        CachedSession session = getOrRestoreSession(request.sessionId());
        List<InterviewQuestionDTO> questions = session.getQuestions(objectMapper);

        int index = request.questionIndex();
        if (index < 0 || index >= questions.size()) {
            throw new BusinessException(ErrorCode.INTERVIEW_QUESTION_NOT_FOUND, "无效的问题索引: " + index);
        }

        // 更新问题答案
        InterviewQuestionDTO question = questions.get(index);
        InterviewQuestionDTO answeredQuestion = question.withAnswer(request.answer());
        questions.set(index, answeredQuestion);

        // 更新 Redis 缓存
        sessionCache.updateQuestions(request.sessionId(), questions);

        // 更新状态为进行中
        if (session.getStatus() == SessionStatus.CREATED) {
            sessionCache.updateSessionStatus(request.sessionId(), SessionStatus.IN_PROGRESS);
        }

        // 保存答案到数据库（不更新currentIndex）
        try {
            persistenceService.saveAnswer(
                request.sessionId(), index,
                question.question(), question.category(),
                request.answer(), 0, null
            );
            persistenceService.updateSessionStatus(request.sessionId(),
                InterviewSessionEntity.SessionStatus.IN_PROGRESS);
        } catch (Exception e) {
            log.warn("暂存答案到数据库失败: {}", e.getMessage());
        }

        log.info("会话 {} 暂存答案: 问题{}", request.sessionId(), index);
    }

    /**
     * 提前交卷（触发异步评估）
     */
    public void completeInterview(String sessionId) {
        CachedSession session = getOrRestoreSession(sessionId);

        if (session.getStatus() == SessionStatus.COMPLETED || session.getStatus() == SessionStatus.EVALUATED) {
            throw new BusinessException(ErrorCode.INTERVIEW_ALREADY_COMPLETED);
        }

        // 更新 Redis 缓存
        sessionCache.updateSessionStatus(sessionId, SessionStatus.COMPLETED);

        // 更新数据库状态
        try {
            persistenceService.updateSessionStatus(sessionId,
                InterviewSessionEntity.SessionStatus.COMPLETED);
            // 设置评估状态为 PENDING
            persistenceService.updateEvaluateStatus(sessionId, AsyncTaskStatus.PENDING, null);
        } catch (Exception e) {
            log.warn("更新会话状态失败: {}", e.getMessage());
        }

        // 发送评估任务到 Redis Stream
        evaluateStreamProducer.sendEvaluateTask(sessionId);

        log.info("会话 {} 提前交卷，评估任务已入队", sessionId);
    }

    /**
     * 获取或恢复会话（优先从缓存获取）
     */
    private CachedSession getOrRestoreSession(String sessionId) {
        // 1. 尝试从 Redis 缓存获取
        Optional<CachedSession> cachedOpt = sessionCache.getSession(sessionId);
        if (cachedOpt.isPresent()) {
            // 刷新 TTL
            sessionCache.refreshSessionTTL(sessionId);
            return cachedOpt.get();
        }

        // 2. 缓存未命中，从数据库恢复
        CachedSession restoredSession = restoreSessionFromDatabase(sessionId);
        if (restoredSession == null) {
            throw new BusinessException(ErrorCode.INTERVIEW_SESSION_NOT_FOUND);
        }

        return restoredSession;
    }

    /**
     * 生成评估报告
     */
    public InterviewReportDTO generateReport(String sessionId) {
        CachedSession session = getOrRestoreSession(sessionId);

        if (session.getStatus() != SessionStatus.COMPLETED && session.getStatus() != SessionStatus.EVALUATED) {
            throw new BusinessException(ErrorCode.INTERVIEW_NOT_COMPLETED, "面试尚未完成，无法生成报告");
        }

        log.info("生成面试报告: {}", sessionId);

        List<InterviewQuestionDTO> questions = session.getQuestions(objectMapper);

        // 获取 LLM 客户端
        String provider = ScenarioDefaults.LLM_PROVIDER;
        Optional<InterviewSessionEntity> entityOpt = persistenceService.findBySessionId(sessionId);
        if (entityOpt.isPresent()) {
            provider = entityOpt.get().getLlmProvider();
        }
        ChatClient chatClient = llmProviderRegistry.getChatClientOrDefault(provider);

        InterviewReportDTO report = evaluationService.evaluateInterview(
            chatClient,
            sessionId,
            session.getResumeText(),
            questions
        );

        // 更新 Redis 缓存状态
        sessionCache.updateSessionStatus(sessionId, SessionStatus.EVALUATED);

        // 保存报告到数据库
        try {
            persistenceService.saveReport(sessionId, report);
        } catch (Exception e) {
            log.warn("保存报告到数据库失败: {}", e.getMessage());
        }

        return report;
    }

    /**
     * 将缓存会话转换为 DTO
     */
    private InterviewSessionDTO toDTO(CachedSession session) {
        List<InterviewQuestionDTO> questions = session.getQuestions(objectMapper);
        return new InterviewSessionDTO(
            session.getSessionId(),
            session.getSimulationDirection() != null
                ? session.getSimulationDirection()
                : SimulationDirection.fromScenarioType(session.getScenarioType()).name(),
            SimulationScenarioType.fromNullable(session.getScenarioType()).id(),
            session.getSimulationDifficulty() != null
                ? session.getSimulationDifficulty()
                : SimulationDifficulty.fromLegacy(session.getDifficulty()).name(),
            session.getResumeText(),
            session.getResumeId(),
            session.getBasedOnDocument() != null ? session.getBasedOnDocument() : session.getResumeId() != null,
            session.getSkillId(),
            countMainQuestions(questions),
            session.getCurrentIndex(),
            questions,
            session.getStatus()
        );
    }
}
