package com.ruici.ai.modules.simulation.service;

import com.ruici.ai.common.constant.CommonConstants.ScenarioDefaults;
import com.ruici.ai.common.exception.BusinessException;
import com.ruici.ai.common.exception.ErrorCode;
import com.ruici.ai.common.model.AsyncTaskStatus;
import com.ruici.ai.modules.simulation.model.HistoricalQuestion;
import com.ruici.ai.modules.simulation.model.InterviewAnswerEntity;
import com.ruici.ai.modules.simulation.model.InterviewQuestionDTO;
import com.ruici.ai.modules.simulation.model.InterviewReportDTO;
import com.ruici.ai.modules.simulation.model.InterviewSessionEntity;
import com.ruici.ai.modules.simulation.model.SimulationScenarioType;
import com.ruici.ai.modules.simulation.repository.InterviewAnswerRepository;
import com.ruici.ai.modules.simulation.repository.InterviewSessionRepository;
import com.ruici.ai.modules.document.model.ResumeEntity;
import com.ruici.ai.modules.document.repository.ResumeRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.JacksonException;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

import java.time.LocalDateTime;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;

/**
 * 情景模拟持久化服务。
 *
 * <p>当前仍复用历史 `Interview*` 仓储与实体，但这里已经把业务语义按“场景会话 + 回答记录”统一整理。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class InterviewPersistenceService {
    
    private final InterviewSessionRepository sessionRepository;
    private final InterviewAnswerRepository answerRepository;
    private final ResumeRepository resumeRepository;
    private final ObjectMapper objectMapper;
    
    /**
     * 保存新的情景模拟会话（支持可选关联文档）。
     */
    @Transactional(rollbackFor = Exception.class)
    public InterviewSessionEntity saveSession(String sessionId, String scenarioType, Long resumeId,
                                              int totalQuestions,
                                              List<InterviewQuestionDTO> questions,
                                              String llmProvider,
                                              String skillId,
                                              String difficulty,
                                              String simulationDirection,
                                              String simulationDifficulty,
                                              Boolean basedOnDocument) {
        try {
            InterviewSessionEntity session = new InterviewSessionEntity();
            session.setSessionId(sessionId);
            session.setScenarioType(SimulationScenarioType.fromNullable(scenarioType).id());
            session.setTotalQuestions(totalQuestions);
            session.setCurrentQuestionIndex(0);
            session.setStatus(InterviewSessionEntity.SessionStatus.CREATED);
            session.setQuestionsJson(objectMapper.writeValueAsString(questions));
            session.setLlmProvider(llmProvider != null ? llmProvider : ScenarioDefaults.LLM_PROVIDER);
            session.setSkillId(skillId != null ? skillId : ScenarioDefaults.SKILL_ID);
            session.setDifficulty(difficulty != null ? difficulty : ScenarioDefaults.DIFFICULTY);
            session.setSimulationDirection(simulationDirection);
            session.setSimulationDifficulty(simulationDifficulty);
            session.setBasedOnDocument(basedOnDocument != null ? basedOnDocument : resumeId != null);

            // 简历可选：有 resumeId 则关联简历
            if (resumeId != null) {
                Optional<ResumeEntity> resumeOpt = resumeRepository.findById(resumeId);
                resumeOpt.ifPresent(session::setResume);
            }

            InterviewSessionEntity saved = sessionRepository.save(session);
            log.info("情景模拟会话已保存: sessionId={}, skillId={}, documentId={}", sessionId, skillId, resumeId);

            return saved;
        } catch (JacksonException e) {
            log.error("序列化问题列表失败: {}", e.getMessage(), e);
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "保存会话失败");
        }
    }
    
    /**
     * 更新会话状态
     */
    @Transactional(rollbackFor = Exception.class)
    public void updateSessionStatus(String sessionId, InterviewSessionEntity.SessionStatus status) {
        Optional<InterviewSessionEntity> sessionOpt = sessionRepository.findBySessionId(sessionId);
        if (sessionOpt.isPresent()) {
            InterviewSessionEntity session = sessionOpt.get();
            session.setStatus(status);
            if (status == InterviewSessionEntity.SessionStatus.COMPLETED ||
                status == InterviewSessionEntity.SessionStatus.EVALUATED) {
                session.setCompletedAt(LocalDateTime.now());
            }
            sessionRepository.save(session);
        }
    }

    /**
     * 更新评估状态
     */
    @Transactional(rollbackFor = Exception.class)
    public void updateEvaluateStatus(String sessionId, AsyncTaskStatus status, String error) {
        Optional<InterviewSessionEntity> sessionOpt = sessionRepository.findBySessionId(sessionId);
        if (sessionOpt.isPresent()) {
            InterviewSessionEntity session = sessionOpt.get();
            session.setEvaluateStatus(status);
            if (error != null) {
                session.setEvaluateError(error.length() > 500 ? error.substring(0, 500) : error);
            } else {
                session.setEvaluateError(null);
            }
            sessionRepository.save(session);
            log.debug("评估状态已更新: sessionId={}, status={}", sessionId, status);
        }
    }
    
    /**
     * 更新当前问题索引
     */
    @Transactional(rollbackFor = Exception.class)
    public void updateCurrentQuestionIndex(String sessionId, int index) {
        Optional<InterviewSessionEntity> sessionOpt = sessionRepository.findBySessionId(sessionId);
        if (sessionOpt.isPresent()) {
            InterviewSessionEntity session = sessionOpt.get();
            session.setCurrentQuestionIndex(index);
            session.setStatus(InterviewSessionEntity.SessionStatus.IN_PROGRESS);
            sessionRepository.save(session);
        }
    }
    
    /**
     * 保存情景回答。
     */
    @Transactional(rollbackFor = Exception.class)
    public InterviewAnswerEntity saveAnswer(String sessionId, int questionIndex,
                                            String question, String category,
                                            String userAnswer, int score, String feedback) {
        Optional<InterviewSessionEntity> sessionOpt = sessionRepository.findBySessionId(sessionId);
        if (sessionOpt.isEmpty()) {
            throw new BusinessException(ErrorCode.INTERVIEW_SESSION_NOT_FOUND);
        }

        InterviewAnswerEntity answer = answerRepository
            .findBySession_SessionIdAndQuestionIndex(sessionId, questionIndex)
            .orElseGet(() -> {
                InterviewAnswerEntity created = new InterviewAnswerEntity();
                created.setSession(sessionOpt.get());
                created.setQuestionIndex(questionIndex);
                return created;
            });

        answer.setQuestion(question);
        answer.setCategory(category);
        answer.setUserAnswer(userAnswer);
        answer.setScore(score);
        answer.setFeedback(feedback);

        InterviewAnswerEntity saved = answerRepository.save(answer);
        log.info("情景回答已保存: sessionId={}, questionIndex={}, score={}", 
                sessionId, questionIndex, score);
        
        return saved;
    }
    
    /**
     * 保存情景模拟报告。
     */
    @Transactional(rollbackFor = Exception.class)
    public void saveReport(String sessionId, InterviewReportDTO report) {
        try {
            Optional<InterviewSessionEntity> sessionOpt = sessionRepository.findBySessionId(sessionId);
            if (sessionOpt.isEmpty()) {
                log.warn("会话不存在: {}", sessionId);
                return;
            }

            InterviewSessionEntity session = sessionOpt.get();
            session.setOverallScore(report.overallScore());
            session.setOverallFeedback(report.overallFeedback());
            session.setStrengthsJson(objectMapper.writeValueAsString(report.strengths()));
            session.setImprovementsJson(objectMapper.writeValueAsString(report.improvements()));
            session.setReferenceAnswersJson(objectMapper.writeValueAsString(report.referenceAnswers()));
            session.setStatus(InterviewSessionEntity.SessionStatus.EVALUATED);
            session.setCompletedAt(LocalDateTime.now());

            sessionRepository.save(session);

            // 查询已存在的答案，建立索引
            List<InterviewAnswerEntity> existingAnswers = answerRepository.findBySession_SessionIdOrderByQuestionIndex(sessionId);
            java.util.Map<Integer, InterviewAnswerEntity> answerMap = existingAnswers.stream()
                .collect(java.util.stream.Collectors.toMap(
                    InterviewAnswerEntity::getQuestionIndex,
                    a -> a,
                    (a1, a2) -> a1
                ));

            // 建立参考答案索引
            java.util.Map<Integer, InterviewReportDTO.ReferenceAnswer> refAnswerMap = report.referenceAnswers().stream()
                .collect(java.util.stream.Collectors.toMap(
                    InterviewReportDTO.ReferenceAnswer::questionIndex,
                    r -> r,
                    (r1, r2) -> r1
                ));

            List<InterviewAnswerEntity> answersToSave = new java.util.ArrayList<>();

            // 遍历所有评估结果，更新或创建答案记录
            for (InterviewReportDTO.QuestionEvaluation eval : report.questionDetails()) {
                InterviewAnswerEntity answer = answerMap.get(eval.questionIndex());

                if (answer == null) {
                    // 未回答的题目，创建新记录
                    answer = new InterviewAnswerEntity();
                    answer.setSession(session);
                    answer.setQuestionIndex(eval.questionIndex());
                    answer.setQuestion(eval.question());
                    answer.setCategory(eval.category());
                    answer.setUserAnswer(null);  // 未回答
                    log.debug("为未回答的题目 {} 创建答案记录", eval.questionIndex());
                }

                // 更新评分和反馈
                answer.setScore(eval.score());
                answer.setFeedback(eval.feedback());

                // 设置参考答案和关键点
                InterviewReportDTO.ReferenceAnswer refAns = refAnswerMap.get(eval.questionIndex());
                if (refAns != null) {
                    answer.setReferenceAnswer(refAns.referenceAnswer());
                    if (refAns.keyPoints() != null && !refAns.keyPoints().isEmpty()) {
                        answer.setKeyPointsJson(objectMapper.writeValueAsString(refAns.keyPoints()));
                    }
                }

                answersToSave.add(answer);
            }

            answerRepository.saveAll(answersToSave);
            log.info("情景模拟报告已保存: sessionId={}, score={}, answers={}",
                sessionId, report.overallScore(), answersToSave.size());

        } catch (JacksonException e) {
            log.error("序列化报告失败: {}", e.getMessage(), e);
        }
    }
    
    /**
     * 根据会话ID获取会话
     */
    public Optional<InterviewSessionEntity> findBySessionId(String sessionId) {
        return sessionRepository.findBySessionIdWithResume(sessionId);
    }
    
    /**
     * 获取某文档的所有场景记录。
     */
    public List<InterviewSessionEntity> findByResumeId(Long resumeId) {
        return sessionRepository.findByDocumentIdOrderByCreatedAtDesc(resumeId);
    }

    /**
     * 获取所有面试记录（按创建时间倒序）
     */
    public List<InterviewSessionEntity> findAll() {
        return sessionRepository.findAllByOrderByCreatedAtDesc();
    }
    
    /**
     * 删除某文档的所有场景会话。
     * 由于InterviewSessionEntity设置了cascade = CascadeType.ALL, orphanRemoval = true
     * 删除会话会自动删除关联的答案
     */
    @Transactional(rollbackFor = Exception.class)
    public void deleteSessionsByResumeId(Long resumeId) {
        List<InterviewSessionEntity> sessions = sessionRepository.findByDocumentIdOrderByCreatedAtDesc(resumeId);
        if (!sessions.isEmpty()) {
            sessionRepository.deleteAll(sessions);
            log.info("已删除 {} 个情景模拟会话（包含所有答案）", sessions.size());
        }
    }
    
    /**
     * 删除单个面试会话
     * 由于InterviewSessionEntity设置了cascade = CascadeType.ALL, orphanRemoval = true
     * 删除会话会自动删除关联的答案
     */
    @Transactional(rollbackFor = Exception.class)
    public void deleteSessionBySessionId(String sessionId) {
        Optional<InterviewSessionEntity> sessionOpt = sessionRepository.findBySessionId(sessionId);
        if (sessionOpt.isPresent()) {
            sessionRepository.delete(sessionOpt.get());
            log.info("已删除情景模拟会话: sessionId={}", sessionId);
        } else {
            throw new BusinessException(ErrorCode.INTERVIEW_SESSION_NOT_FOUND);
        }
    }
    
    /**
     * 查找未完成的情景模拟会话（CREATED 或 IN_PROGRESS 状态）。
     */
    public Optional<InterviewSessionEntity> findUnfinishedSession(Long resumeId) {
        List<InterviewSessionEntity.SessionStatus> unfinishedStatuses = List.of(
            InterviewSessionEntity.SessionStatus.CREATED,
            InterviewSessionEntity.SessionStatus.IN_PROGRESS
        );
        return sessionRepository.findFirstByDocumentIdAndStatusInOrderByCreatedAtDesc(resumeId, unfinishedStatuses);
    }

    /**
     * 按文档 + 场景 + 模板查找未完成会话。
     *
     * <p>相比只按文档查找，这个方法可以避免不同场景共用同一条未完成记录。</p>
     */
    public Optional<InterviewSessionEntity> findUnfinishedSession(Long resumeId, String scenarioType, String skillId) {
        List<InterviewSessionEntity.SessionStatus> unfinishedStatuses = List.of(
            InterviewSessionEntity.SessionStatus.CREATED,
            InterviewSessionEntity.SessionStatus.IN_PROGRESS
        );
        return sessionRepository.findFirstByDocumentIdAndScenarioTypeAndSkillIdAndStatusInOrderByCreatedAtDesc(
            resumeId,
            scenarioType,
            skillId,
            unfinishedStatuses
        );
    }
    
    /**
     * 根据会话ID查找所有答案
     */
    public List<InterviewAnswerEntity> findAnswersBySessionId(String sessionId) {
        return answerRepository.findBySession_SessionIdOrderByQuestionIndex(sessionId);
    }

    private static final int MAX_HISTORICAL_QUESTIONS = 60;

    /**
     * 获取历史提问列表（结构化，按分类压缩用）。
     * 有 resumeId 时精确匹配 resumeId + skillId；无 resumeId 时按 skillId 查全部（通用模式兜底）。
     */
    public List<HistoricalQuestion> getHistoricalQuestions(String scenarioType, String skillId, Long resumeId) {
        List<InterviewSessionEntity> sessions;
        if (resumeId != null) {
            sessions = sessionRepository.findTop10ByResumeIdAndScenarioTypeAndSkillIdOrderByCreatedAtDesc(
                resumeId, scenarioType, skillId
            );
        } else {
            sessions = sessionRepository.findTop10ByScenarioTypeAndSkillIdOrderByCreatedAtDesc(scenarioType, skillId);
        }

        log.info("加载历史题目: scenarioType={}, skillId={}, resumeId={}, 查到 {} 个历史会话",
            scenarioType, skillId, resumeId, sessions.size());

        LinkedHashSet<String> seen = new LinkedHashSet<>();
        List<HistoricalQuestion> result = sessions.stream()
            .map(InterviewSessionEntity::getQuestionsJson)
            .filter(json -> json != null && !json.isEmpty())
            .flatMap(json -> {
                try {
                    List<InterviewQuestionDTO> questions = objectMapper.readValue(json,
                        new TypeReference<List<InterviewQuestionDTO>>() {});
                    return questions.stream()
                        .filter(q -> !q.isFollowUp())
                        .map(q -> new HistoricalQuestion(q.question(), q.type(), q.topicSummary()));
                } catch (Exception e) {
                    log.error("解析历史问题JSON失败", e);
                    return java.util.stream.Stream.<HistoricalQuestion>empty();
                }
            })
            .filter(hq -> {
                String dedupKey = hq.topicSummary() != null && !hq.topicSummary().isBlank()
                    ? hq.topicSummary()
                    : hq.question();
                return seen.add(dedupKey);
            })
            .limit(MAX_HISTORICAL_QUESTIONS)
            .toList();

        log.info("历史题目加载完成: 去重后 {} 道主问题，按分类: {}", result.size(),
            result.stream().collect(java.util.stream.Collectors.groupingBy(
                hq -> hq.type() != null ? hq.type() : "GENERAL",
                java.util.stream.Collectors.counting())));

        return result;
    }
}
