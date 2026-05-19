package com.ruici.ai.modules.simulation.service;

import com.ruici.ai.common.ai.LlmProviderRegistry;
import com.ruici.ai.common.ai.StructuredOutputInvoker;
import com.ruici.ai.common.constant.CommonConstants.ScenarioDefaults;
import com.ruici.ai.common.exception.BusinessException;
import com.ruici.ai.common.exception.ErrorCode;
import com.ruici.ai.modules.simulation.model.HistoricalQuestion;
import com.ruici.ai.modules.simulation.model.InterviewQuestionDTO;
import com.ruici.ai.modules.simulation.model.SimulationScenarioType;
import com.ruici.ai.modules.simulation.skill.InterviewSkillService;
import com.ruici.ai.modules.simulation.skill.InterviewSkillService.CategoryDTO;
import com.ruici.ai.modules.simulation.skill.InterviewSkillService.SkillDTO;
import com.ruici.ai.modules.simulation.skill.InterviewSkillService.SkillCategoryDTO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.prompt.PromptTemplate;
import org.springframework.ai.converter.BeanOutputConverter;
import org.springframework.core.io.ResourceLoader;
import org.springframework.stereotype.Service;

import jakarta.annotation.PreDestroy;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;

/**
 * 面试问题生成服务
 * 无简历：单次 Skill 驱动出题
 * 有简历：并行调用（简历题 60% + 方向题 40%）
 */
@Service
public class InterviewQuestionService {

    private static final Logger log = LoggerFactory.getLogger(InterviewQuestionService.class);

    private static final String DEFAULT_QUESTION_TYPE = "GENERAL";
    private static final int MAX_FOLLOW_UP_COUNT = 2;
    private static final double RESUME_QUESTION_RATIO = 0.6;

    private static final String GENERIC_MODE_SYSTEM_APPEND = """
        \n\n# 通用场景模式
        本次会话无用户文档上下文，请按当前场景生成标准主问题。
        - 禁止出现“你在简历中提到...”等假设用户一定提供文档的表述
        - 问题应直接围绕场景目标展开，避免无关跳题
        """;

    private static final String[][] GENERIC_FALLBACK_QUESTIONS = {
        {"请描述一个你主导解决的技术难题，你的分析思路是什么？", "GENERAL", "综合能力"},
        {"你在做技术方案选型时，通常考虑哪些因素？请举例说明。", "GENERAL", "综合能力"},
        {"请分享一次你处理线上故障的经历，从发现到修复的完整过程。", "GENERAL", "综合能力"},
        {"你如何保证代码质量？介绍你实践过的有效手段。", "GENERAL", "综合能力"},
        {"描述一个你做过的技术优化案例，优化的动机、方案和效果。", "GENERAL", "综合能力"},
        {"你在团队协作中遇到过最大的分歧是什么？如何解决的？", "GENERAL", "综合能力"},
    };

    private final PromptTemplate skillSystemPromptTemplate;
    private final PromptTemplate skillUserPromptTemplate;
    private final PromptTemplate resumeSystemPromptTemplate;
    private final PromptTemplate resumeUserPromptTemplate;
    private final BeanOutputConverter<QuestionListDTO> outputConverter;
    private final StructuredOutputInvoker structuredOutputInvoker;
    private final InterviewSkillService skillService;
    private final LlmProviderRegistry llmProviderRegistry;
    private final ExecutorService questionExecutor;
    private final int followUpCount;

    private record QuestionListDTO(List<QuestionDTO> questions) {}

    private record QuestionDTO(String question, String type, String category,
                               String topicSummary, List<String> followUps) {}

    public InterviewQuestionService(
            StructuredOutputInvoker structuredOutputInvoker,
            InterviewSkillService skillService,
            InterviewQuestionProperties properties,
            ResourceLoader resourceLoader,
            LlmProviderRegistry llmProviderRegistry) throws IOException {
        this.structuredOutputInvoker = structuredOutputInvoker;
        this.skillService = skillService;
        this.llmProviderRegistry = llmProviderRegistry;
        this.questionExecutor = Executors.newVirtualThreadPerTaskExecutor();
        this.skillSystemPromptTemplate = loadTemplate(resourceLoader, properties.getQuestionSystemPromptPath());
        this.skillUserPromptTemplate = loadTemplate(resourceLoader, properties.getQuestionUserPromptPath());
        this.resumeSystemPromptTemplate = loadTemplate(resourceLoader, properties.getResumeQuestionSystemPromptPath());
        this.resumeUserPromptTemplate = loadTemplate(resourceLoader, properties.getResumeQuestionUserPromptPath());
        this.outputConverter = new BeanOutputConverter<>(QuestionListDTO.class);
        this.followUpCount = Math.max(0, Math.min(properties.getFollowUpCount(), MAX_FOLLOW_UP_COUNT));
    }

    private static PromptTemplate loadTemplate(ResourceLoader loader, String location) throws IOException {
        return new PromptTemplate(loader.getResource(location).getContentAsString(StandardCharsets.UTF_8));
    }

    @PreDestroy
    void destroy() {
        questionExecutor.shutdownNow();
    }

    public List<InterviewQuestionDTO> generateQuestionsBySkill(
            ChatClient chatClient,
            SimulationScenarioType scenarioType,
            String skillId,
            String difficulty,
            String resumeText,
            int questionCount,
            List<HistoricalQuestion> historicalQuestions,
            List<CategoryDTO> customCategories,
            String jdText) {

        SkillDTO skill = resolveSkill(skillId, customCategories, jdText, scenarioType);
        String difficultyDesc = resolveDifficulty(scenarioType, difficulty);

        boolean hasResume = resumeText != null && !resumeText.isBlank();
        String historicalSection = buildHistoricalSection(historicalQuestions);
        if (!hasResume) {
            return ensureDistinctMainQuestions(generateDirectionOnly(
                chatClient, scenarioType, skill, difficultyDesc, questionCount, historicalSection
            ), questionCount, skill);
        }

        int resumeCount = Math.max(1, (int) Math.round(questionCount * RESUME_QUESTION_RATIO));
        int directionCount = questionCount - resumeCount;

        log.info("并行出题: skill={}, total={}, resumeCount={}, directionCount={}",
            skillId, questionCount, resumeCount, directionCount);

        CompletableFuture<List<InterviewQuestionDTO>> resumeFuture = CompletableFuture.supplyAsync(
            () -> generateResumeQuestions(
                scenarioType, resumeText, resumeCount, skill, difficultyDesc, historicalSection
            ),
            questionExecutor);

        CompletableFuture<List<InterviewQuestionDTO>> directionFuture = CompletableFuture.supplyAsync(
            () -> generateDirectionOnly(
                chatClient, scenarioType, skill, difficultyDesc, directionCount, historicalSection
            ),
            questionExecutor);

        List<InterviewQuestionDTO> resumeQuestions;
        List<InterviewQuestionDTO> directionQuestions;
        try {
            resumeQuestions = resumeFuture.join();
        } catch (CompletionException e) {
            log.error("简历题生成失败，降级为全方向题", e.getCause());
            directionFuture.cancel(true);
            return ensureDistinctMainQuestions(generateDirectionOnly(
                chatClient, scenarioType, skill, difficultyDesc, questionCount, historicalSection
            ), questionCount, skill);
        }

        try {
            directionQuestions = directionFuture.join();
        } catch (CompletionException e) {
            log.error("方向题生成失败，降级为全简历题", e.getCause());
            if (resumeQuestions.isEmpty()) {
                return generateFallbackQuestions(skill, questionCount);
            }
            return ensureDistinctMainQuestions(resumeQuestions, questionCount, skill);
        }

        if (resumeQuestions.isEmpty() && directionQuestions.isEmpty()) {
            log.warn("简历题和方向题均为空，回退到默认问题");
            return generateFallbackQuestions(skill, questionCount);
        }

        List<InterviewQuestionDTO> merged = mergeQuestionBatches(resumeQuestions, directionQuestions);
        log.info("并行出题成功: 简历题={}, 方向题={}, 合计={}",
            resumeQuestions.size(), directionQuestions.size(), merged.size());
        return ensureDistinctMainQuestions(merged, questionCount, skill);
    }

    private List<InterviewQuestionDTO> generateResumeQuestions(
            SimulationScenarioType scenarioType,
            String resumeText,
            int questionCount,
            SkillDTO skill, String difficultyDesc, String historicalSection) {
        try {
            ChatClient plainClient = llmProviderRegistry.getPlainChatClient(null);
            Map<String, Object> variables = new HashMap<>();
            variables.put("questionCount", questionCount);
            variables.put("followUpCount", followUpCount);
            variables.put("scenarioName", scenarioType.displayName());
            variables.put("scenarioObjective", scenarioType.objective());
            variables.put("scenarioQuestionStyle", scenarioType.questionStyle());
            variables.put("aiRoleLabel", scenarioType.aiRoleLabel());
            variables.put("skillName", skill.name());
            variables.put("skillDescription", skill.description() != null ? skill.description() : "");
            variables.put("difficultyDescription", difficultyDesc);
            variables.put("resumeText", resumeText);
            variables.put("historicalSection", historicalSection);

            String systemPrompt = resumeSystemPromptTemplate.render() + "\n\n" + outputConverter.getFormat();
            String userPrompt = resumeUserPromptTemplate.render(variables);

            QuestionListDTO dto = structuredOutputInvoker.invoke(
                plainClient, systemPrompt, userPrompt, outputConverter,
                ErrorCode.INTERVIEW_QUESTION_GENERATION_FAILED,
                "简历题生成失败：", "简历题", log);

            List<InterviewQuestionDTO> questions = convertToQuestions(dto);
            questions = capToMainCount(questions, questionCount);
            log.info("简历题生成完成: 请求={}, 实际主问题={}",
                questionCount, questions.stream().filter(q -> !q.isFollowUp()).count());
            return questions;
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.error("简历题生成异常: {}", e.getMessage(), e);
            throw e;
        }
    }

    private List<InterviewQuestionDTO> generateDirectionOnly(
            ChatClient chatClient, SimulationScenarioType scenarioType,
            SkillDTO skill, String difficultyDesc,
            int questionCount, String historicalSection) {
        Map<String, Integer> allocation = skillService.calculateAllocation(skill.categories(), questionCount);
        String allocationTable = skillService.buildAllocationDescription(allocation, skill.categories());

        log.info("方向题生成: skill={}, total={}, allocation={}",
            skill.id(), questionCount, allocation);

        try {
            Map<String, Object> variables = new HashMap<>();
            variables.put("questionCount", questionCount);
            variables.put("followUpCount", followUpCount);
            variables.put("difficultyDescription", difficultyDesc);
            variables.put("skillName", skill.name());
            variables.put("skillDescription", skill.description() != null ? skill.description() : "");
            variables.put("skillToolCommand", skill.id());
            variables.put("scenarioName", scenarioType.displayName());
            variables.put("scenarioObjective", scenarioType.objective());
            variables.put("scenarioQuestionStyle", scenarioType.questionStyle());
            variables.put("aiRoleLabel", scenarioType.aiRoleLabel());
            variables.put("allocationTable", allocationTable);
            variables.put("historicalSection", historicalSection);
            variables.put("referenceSection", skillService.buildReferenceSection(skill, allocation));
            variables.put("jdSection", buildJdSection(skill.sourceJd()));

            String systemPrompt = skillSystemPromptTemplate.render()
                + GENERIC_MODE_SYSTEM_APPEND + outputConverter.getFormat();
            String userPrompt = skillUserPromptTemplate.render(variables);

            QuestionListDTO dto = structuredOutputInvoker.invoke(
                chatClient, systemPrompt, userPrompt, outputConverter,
                ErrorCode.INTERVIEW_QUESTION_GENERATION_FAILED,
                "方向题生成失败：", "方向题", log);

            List<InterviewQuestionDTO> questions = convertToQuestions(dto);
            if (questions.stream().filter(q -> !q.isFollowUp()).count() == 0) {
                log.warn("方向题返回空题单，回退到默认问题");
                return generateFallbackQuestions(skill, questionCount);
            }
            questions = capToMainCount(questions, questionCount);
            log.info("方向题生成完成: 请求={}, 实际主问题={}",
                questionCount, questions.stream().filter(q -> !q.isFollowUp()).count());
            return questions;
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.error("方向题生成失败，回退到默认问题: {}", e.getMessage(), e);
            return generateFallbackQuestions(skill, questionCount);
        }
    }

    private List<InterviewQuestionDTO> mergeQuestionBatches(
            List<InterviewQuestionDTO> first, List<InterviewQuestionDTO> second) {
        if (second.isEmpty()) {
            return first;
        }
        if (first.isEmpty()) {
            return second;
        }
        int offset = first.size();
        List<InterviewQuestionDTO> merged = new ArrayList<>(first);
        for (InterviewQuestionDTO q : second) {
            int newIndex = q.questionIndex() + offset;
            Integer newParent = q.parentQuestionIndex() != null
                ? q.parentQuestionIndex() + offset : null;
            merged.add(InterviewQuestionDTO.create(
                newIndex, q.question(), q.type(), q.category(),
                q.topicSummary(), q.isFollowUp(), newParent));
        }
        return merged;
    }

    private SkillDTO resolveSkill(
            String skillId,
            List<CategoryDTO> customCategories,
            String jdText,
            SimulationScenarioType scenarioType) {
        if (InterviewSkillService.CUSTOM_SKILL_ID.equals(skillId)) {
            if (customCategories != null && !customCategories.isEmpty()) {
                return skillService.buildCustomSkill(customCategories, jdText != null ? jdText : "");
            }
            return skillService.buildFallbackCustomSkill(scenarioType, jdText != null ? jdText : "");
        }
        return skillService.getSkill(skillId);
    }

    private String resolveDifficulty(SimulationScenarioType scenarioType, String difficulty) {
        String effectiveDifficulty = difficulty != null ? difficulty : ScenarioDefaults.DIFFICULTY;
        return switch (scenarioType) {
            case PROFESSIONAL_QA, TCM_QA -> switch (effectiveDifficulty) {
                case "junior" -> "新手级：优先考察基础概念理解、清晰讲解与入门场景说明。";
                case "senior" -> "老练级：强调深度辨析、边界条件、反问追问与复杂情境解释。";
                default -> "普通级：侧重原理拆解、案例分析、方案权衡与专业判断。";
            };
            case WORKPLACE_COMMUNICATION, NOVEL_EXPERT -> switch (effectiveDifficulty) {
                case "junior" -> "新手级：聚焦日常协作、基础反馈与礼貌清晰表达。";
                case "senior" -> "老练级：强调冲突处理、复杂博弈、向上沟通与结果推进。";
                default -> "普通级：围绕会议沟通、跨团队协作、进度同步与反馈处理展开。";
            };
            default -> switch (effectiveDifficulty) {
                case "junior" -> "新手级：重点考察基础能力、岗位理解与入门表达。";
                case "senior" -> "老练级：重点考察复杂场景拆解、关键决策与高压追问。";
                default -> "普通级：重点考察项目经验、方案权衡与岗位匹配度。";
            };
        };
    }

    private List<InterviewQuestionDTO> ensureDistinctMainQuestions(
            List<InterviewQuestionDTO> questions, int targetMainCount, SkillDTO skill) {
        Map<Integer, List<InterviewQuestionDTO>> grouped = new LinkedHashMap<>();
        for (InterviewQuestionDTO question : questions) {
            int mainIndex = question.isFollowUp() && question.parentQuestionIndex() != null
                ? question.parentQuestionIndex()
                : question.questionIndex();
            grouped.computeIfAbsent(mainIndex, ignored -> new ArrayList<>()).add(question);
        }

        LinkedHashSet<String> seenKeys = new LinkedHashSet<>();
        List<InterviewQuestionDTO> deduplicated = new ArrayList<>();
        int nextIndex = 0;
        for (List<InterviewQuestionDTO> group : grouped.values()) {
            InterviewQuestionDTO mainQuestion = group.getFirst();
            if (!seenKeys.add(buildQuestionDedupKey(mainQuestion))) {
                continue;
            }
            int newMainIndex = nextIndex;
            deduplicated.add(reindexQuestion(mainQuestion, newMainIndex, null));
            nextIndex++;

            for (InterviewQuestionDTO item : group) {
                if (!item.isFollowUp()) {
                    continue;
                }
                deduplicated.add(reindexQuestion(item, nextIndex, newMainIndex));
                nextIndex++;
            }
        }

        long mainCount = deduplicated.stream().filter(q -> !q.isFollowUp()).count();
        if (mainCount >= targetMainCount) {
            return deduplicated;
        }

        for (InterviewQuestionDTO fallbackQuestion : generateFallbackQuestions(skill, targetMainCount)) {
            if (fallbackQuestion.isFollowUp()) {
                continue;
            }
            if (!seenKeys.add(buildQuestionDedupKey(fallbackQuestion))) {
                continue;
            }
            int mainIndex = nextIndex;
            deduplicated.add(reindexQuestion(fallbackQuestion, mainIndex, null));
            nextIndex++;
            for (InterviewQuestionDTO fallbackFollowUp : generateFallbackFollowUps(fallbackQuestion, mainIndex, nextIndex)) {
                deduplicated.add(fallbackFollowUp);
                nextIndex++;
            }
            mainCount++;
            if (mainCount >= targetMainCount) {
                break;
            }
        }

        return deduplicated;
    }

    private List<InterviewQuestionDTO> generateFallbackFollowUps(
            InterviewQuestionDTO mainQuestion, int mainIndex, int nextIndexStart) {
        List<InterviewQuestionDTO> followUps = new ArrayList<>();
        int nextIndex = nextIndexStart;
        for (int order = 1; order <= followUpCount; order++) {
            followUps.add(InterviewQuestionDTO.create(
                nextIndex++,
                buildDefaultFollowUp(mainQuestion.question(), order),
                mainQuestion.type(),
                buildFollowUpCategory(mainQuestion.category(), order),
                null,
                true,
                mainIndex
            ));
        }
        return followUps;
    }

    private InterviewQuestionDTO reindexQuestion(
            InterviewQuestionDTO question, int newIndex, Integer parentQuestionIndex) {
        return InterviewQuestionDTO.create(
            newIndex,
            question.question(),
            question.type(),
            question.category(),
            question.topicSummary(),
            question.isFollowUp(),
            parentQuestionIndex
        );
    }

    private String buildQuestionDedupKey(InterviewQuestionDTO question) {
        if (question.topicSummary() != null && !question.topicSummary().isBlank()) {
            return normalizeDedupText(question.topicSummary());
        }
        return normalizeDedupText(question.question());
    }

    private String normalizeDedupText(String text) {
        return text == null ? "" : text
            .replaceAll("[\\p{Punct}\\p{IsPunctuation}\\s]+", "")
            .toLowerCase();
    }

    private List<InterviewQuestionDTO> convertToQuestions(QuestionListDTO dto) {
        List<InterviewQuestionDTO> questions = new ArrayList<>();
        int index = 0;

        if (dto == null || dto.questions() == null) {
            return questions;
        }

        for (QuestionDTO q : dto.questions()) {
            if (q == null || q.question() == null || q.question().isBlank()) {
                continue;
            }
            String type = (q.type() != null && !q.type().isBlank()) ? q.type().toUpperCase() : DEFAULT_QUESTION_TYPE;
            int mainQuestionIndex = index;
            questions.add(InterviewQuestionDTO.create(index++, q.question(), type, q.category(), q.topicSummary(), false, null));

            List<String> followUps = sanitizeFollowUps(q.followUps());
            for (int i = 0; i < followUps.size(); i++) {
                questions.add(InterviewQuestionDTO.create(
                    index++, followUps.get(i), type,
                    buildFollowUpCategory(q.category(), i + 1), null, true, mainQuestionIndex
                ));
            }
        }

        return questions;
    }

    /**
     * 将问题列表截断到指定的主问题数量（AI 多生时截断，少生时保留原样并记录警告）。
     */
    private List<InterviewQuestionDTO> capToMainCount(
            List<InterviewQuestionDTO> questions, int maxMainCount) {
        long currentMainCount = questions.stream().filter(q -> !q.isFollowUp()).count();

        if (currentMainCount <= maxMainCount) {
            if (currentMainCount < maxMainCount) {
                log.warn("AI 生成主问题不足: 请求={}, 实际={}", maxMainCount, currentMainCount);
            }
            return questions;
        }

        List<InterviewQuestionDTO> capped = new ArrayList<>();
        int mainSeen = 0;
        for (InterviewQuestionDTO q : questions) {
            if (!q.isFollowUp()) {
                mainSeen++;
            }
            if (mainSeen > maxMainCount) {
                break;
            }
            capped.add(q);
        }
        log.info("题目截断: 主问题 {} → {}", currentMainCount, maxMainCount);
        return capped;
    }

    private List<InterviewQuestionDTO> generateFallbackQuestions(SkillDTO skill, int count) {
        List<SkillCategoryDTO> categories = skill != null ? skill.categories() : List.of();
        List<InterviewQuestionDTO> questions = new ArrayList<>();
        int index = 0;

        if (!categories.isEmpty()) {
            int generated = 0;
            while (generated < count) {
                SkillCategoryDTO cat = categories.get(generated % categories.size());
                String question = "请谈谈你在\"" + cat.label() + "\"方向的技术理解和实践经验。";
                questions.add(InterviewQuestionDTO.create(index++, question, cat.key(), cat.label(), null, false, null));
                int mainIndex = index - 1;
                for (int j = 0; j < followUpCount; j++) {
                    questions.add(InterviewQuestionDTO.create(
                        index++, buildDefaultFollowUp(question, j + 1),
                        cat.key(), buildFollowUpCategory(cat.label(), j + 1), null, true, mainIndex
                    ));
                }
                generated++;
            }
            return questions;
        }

        for (int i = 0; i < Math.min(count, GENERIC_FALLBACK_QUESTIONS.length); i++) {
            String[] q = GENERIC_FALLBACK_QUESTIONS[i];
            questions.add(InterviewQuestionDTO.create(index++, q[0], q[1], q[2], null, false, null));
            int mainIndex = index - 1;
            for (int j = 0; j < followUpCount; j++) {
                questions.add(InterviewQuestionDTO.create(
                    index++, buildDefaultFollowUp(q[0], j + 1),
                    q[1], buildFollowUpCategory(q[2], j + 1), null, true, mainIndex
                ));
            }
        }
        return questions;
    }

    private String buildHistoricalSection(List<HistoricalQuestion> historicalQuestions) {
        if (historicalQuestions == null || historicalQuestions.isEmpty()) {
            return "暂无历史提问";
        }

        Map<String, List<String>> grouped = new HashMap<>();
        for (HistoricalQuestion hq : historicalQuestions) {
            String type = hq.type() != null && !hq.type().isBlank() ? hq.type() : DEFAULT_QUESTION_TYPE;
            String summary = hq.topicSummary();
            if (summary == null || summary.isBlank()) {
                String q = hq.question();
                summary = q.length() > 30 ? q.substring(0, 30) + "…" : q;
            }
            grouped.computeIfAbsent(type, k -> new ArrayList<>()).add(summary);
        }

        StringBuilder sb = new StringBuilder("已考过的知识点（避免重复出题）：\n");
        for (Map.Entry<String, List<String>> entry : grouped.entrySet()) {
            sb.append("- ").append(entry.getKey()).append(": ");
            sb.append(String.join(", ", entry.getValue()));
            sb.append('\n');
        }
        return sb.toString();
    }

    private String buildJdSection(String sourceJd) {
        if (sourceJd == null || sourceJd.isBlank()) {
            return "";
        }
        return "## 职位描述（JD）\n根据以下 JD 关键要求出题，确保题目与岗位实际需求相关：\n" + sourceJd;
    }

    private List<String> sanitizeFollowUps(List<String> followUps) {
        if (followUpCount == 0 || followUps == null || followUps.isEmpty()) {
            return List.of();
        }
        return followUps.stream()
            .filter(item -> item != null && !item.isBlank())
            .map(String::trim)
            .limit(followUpCount)
            .collect(Collectors.toList());
    }

    private String buildFollowUpCategory(String category, int order) {
        String base = (category == null || category.isBlank()) ? "追问" : category;
        return base + "（追问" + order + "）";
    }

    private String buildDefaultFollowUp(String mainQuestion, int order) {
        if (order == 1) {
            return "基于\"" + mainQuestion + "\"，请结合你亲自做过的一个真实场景展开说明。";
        }
        return "基于\"" + mainQuestion + "\"，如果线上出现异常，你会如何定位并给出修复方案？";
    }
}
