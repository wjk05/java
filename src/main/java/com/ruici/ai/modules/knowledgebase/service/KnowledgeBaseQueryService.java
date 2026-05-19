package com.ruici.ai.modules.knowledgebase.service;

import com.ruici.ai.common.ai.LlmProviderRegistry;
import com.ruici.ai.common.ai.OpenAiCompatibleGatewayClient;
import com.ruici.ai.common.exception.BusinessException;
import com.ruici.ai.common.exception.ErrorCode;
import com.ruici.ai.modules.knowledgebase.model.QueryRequest;
import com.ruici.ai.modules.knowledgebase.model.QueryResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.prompt.PromptTemplate;
import org.springframework.ai.document.Document;
import org.springframework.core.io.ResourceLoader;
import org.springframework.stereotype.Service;
import reactor.core.Disposable;
import reactor.core.publisher.Flux;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 知识库查询服务
 * 基于向量搜索的RAG问答
 */
@Slf4j
@Service
public class KnowledgeBaseQueryService {
    private static final String NO_RESULT_RESPONSE = "抱歉，在选定的知识库中未检索到相关信息。请换一个更具体的关键词或补充上下文后再试。";
    private static final String EMPTY_QUESTION_RESPONSE = "请输入具体问题后再试。";
    private static final String GENERAL_ANSWER_SYSTEM_PROMPT = """
        你是一位专业、可靠的中文问答助手。
        请直接回答用户问题；如果当前没有知识库上下文或知识库未命中，请先用一句话说明这一点，
        然后基于通用知识给出尽量准确、清晰、不过度夸大的回答。
        不要假装引用了知识库，不要编造来源，不确定时要明确说明。
        回答保持结构化、自然、实用。
        """;
    private static final int STREAM_PROBE_CHARS = 120;
    private static final int MAX_REWRITE_HISTORY_CHAR = 200;

    private final ChatClient chatClient;
    private final OpenAiCompatibleGatewayClient gatewayClient;
    private final String providerId;
    private final KnowledgeBaseVectorService vectorService;
    private final KnowledgeBaseListService listService;
    private final KnowledgeBaseCountService countService;
    private final PromptTemplate systemPromptTemplate;
    private final PromptTemplate userPromptTemplate;
    private final PromptTemplate rewritePromptTemplate;
    private final boolean rewriteEnabled;
    private final int shortQueryLength;
    private final int topkShort;
    private final int topkMedium;
    private final int topkLong;
    private final double minScoreShort;
    private final double minScoreDefault;

    public KnowledgeBaseQueryService(
            LlmProviderRegistry llmProviderRegistry,
            OpenAiCompatibleGatewayClient gatewayClient,
            KnowledgeBaseVectorService vectorService,
            KnowledgeBaseListService listService,
            KnowledgeBaseCountService countService,
            KnowledgeBaseQueryProperties queryProperties,
            ResourceLoader resourceLoader) throws IOException {
        this.providerId = queryProperties.getLlmProvider();
        this.chatClient = llmProviderRegistry.getChatClientOrDefault(this.providerId);
        this.gatewayClient = gatewayClient;
        this.vectorService = vectorService;
        this.listService = listService;
        this.countService = countService;
        this.systemPromptTemplate = new PromptTemplate(
            resourceLoader.getResource(queryProperties.getSystemPromptPath())
                .getContentAsString(StandardCharsets.UTF_8)
        );
        this.userPromptTemplate = new PromptTemplate(
            resourceLoader.getResource(queryProperties.getUserPromptPath())
                .getContentAsString(StandardCharsets.UTF_8)
        );
        this.rewritePromptTemplate = new PromptTemplate(
            resourceLoader.getResource(queryProperties.getRewritePromptPath())
                .getContentAsString(StandardCharsets.UTF_8)
        );
        this.rewriteEnabled = queryProperties.getRewrite().isEnabled();
        this.shortQueryLength = queryProperties.getSearch().getShortQueryLength();
        this.topkShort = queryProperties.getSearch().getTopkShort();
        this.topkMedium = queryProperties.getSearch().getTopkMedium();
        this.topkLong = queryProperties.getSearch().getTopkLong();
        this.minScoreShort = queryProperties.getSearch().getMinScoreShort();
        this.minScoreDefault = queryProperties.getSearch().getMinScoreDefault();
    }

    /**
     * 基于单个知识库回答用户问题
     *
     * @param knowledgeBaseId 知识库ID
     * @param question 用户问题
     * @return AI回答
     */
    public String answerQuestion(Long knowledgeBaseId, String question) {
        return answerQuestion(List.of(knowledgeBaseId), question);
    }

    /**
     * 基于多个知识库回答用户问题（RAG）
     *
     * @param knowledgeBaseIds 知识库ID列表
     * @param question 用户问题
     * @return AI回答
     */
    public String answerQuestion(List<Long> knowledgeBaseIds, String question) {
        log.info("收到知识库提问: kbIds={}, question={}", knowledgeBaseIds, question);
        List<Long> normalizedKnowledgeBaseIds = normalizeKnowledgeBaseIds(knowledgeBaseIds);
        String normalizedQuestion = normalizeQuestion(question);

        if (normalizedQuestion.isBlank()) {
            return EMPTY_QUESTION_RESPONSE;
        }

        if (normalizedKnowledgeBaseIds.isEmpty()) {
            return answerWithoutKnowledgeBase(normalizedQuestion, "当前未选择知识库");
        }

        countService.updateQuestionCounts(normalizedKnowledgeBaseIds);

        QueryContext queryContext = buildQueryContext(normalizedQuestion, List.of());
        List<Document> relevantDocs = retrieveRelevantDocs(queryContext, normalizedKnowledgeBaseIds);

        if (!hasEffectiveHit(relevantDocs)) {
            return answerWithoutKnowledgeBase(normalizedQuestion, "当前知识库未检索到相关内容");
        }

        String context = relevantDocs.stream()
                .map(Document::getText)
                .collect(Collectors.joining("\n\n---\n\n"));

        String systemPrompt = buildSystemPrompt();
        String userPrompt = buildUserPrompt(context, normalizedQuestion);

        try {
            String answer = useGatewayCompatibility()
                ? gatewayClient.generateText(providerId, systemPrompt, userPrompt)
                : chatClient.prompt()
                    .system(systemPrompt)
                    .user(userPrompt)
                    .call()
                    .content();
            answer = normalizeAnswer(answer);

            log.info("知识库问答完成: kbIds={}", normalizedKnowledgeBaseIds);
            return answer;

        } catch (Exception e) {
            log.error("知识库问答失败: {}", e.getMessage(), e);
            throw new BusinessException(ErrorCode.KNOWLEDGE_BASE_QUERY_FAILED, "知识库查询失败：" + e.getMessage());
        }
    }

    /**
     * 构建系统提示词
     */
    private String buildSystemPrompt() {
        return systemPromptTemplate.render();
    }

    /**
     * 构建用户提示词
     */
    private String buildUserPrompt(String context, String question) {
        Map<String, Object> variables = new HashMap<>();
        variables.put("context", context);
        variables.put("question", question);
        return userPromptTemplate.render(variables);
    }

    /**
     * 查询知识库并返回完整响应
     */
    public QueryResponse queryKnowledgeBase(QueryRequest request) {
        String answer = answerQuestion(request.knowledgeBaseIds(), request.question());

        List<Long> knowledgeBaseIds = normalizeKnowledgeBaseIds(request.knowledgeBaseIds());

        // 获取知识库名称（多个知识库用逗号分隔）
        List<String> kbNames = knowledgeBaseIds.isEmpty()
            ? List.of()
            : listService.getKnowledgeBaseNames(knowledgeBaseIds);
        String kbNamesStr = String.join("、", kbNames);

        // 使用第一个知识库ID作为主要标识（兼容前端）
        Long primaryKbId = knowledgeBaseIds.isEmpty() ? null : knowledgeBaseIds.getFirst();

        return new QueryResponse(answer, primaryKbId, kbNamesStr);
    }

    /**
     * 流式查询知识库（SSE，无上下文）
     *
     * @param knowledgeBaseIds 知识库ID列表
     * @param question 用户问题
     * @return 流式响应
     */
    public Flux<String> answerQuestionStream(List<Long> knowledgeBaseIds, String question) {
        return answerQuestionStream(knowledgeBaseIds, question, List.of());
    }

    /**
     * 流式查询知识库（SSE，支持多轮上下文）
     *
     * @param knowledgeBaseIds 知识库ID列表
     * @param question 用户问题
     * @param history 历史对话消息（可选）
     * @return 流式响应
     */
    public Flux<String> answerQuestionStream(List<Long> knowledgeBaseIds, String question, List<Message> history) {
        log.info("收到知识库流式提问: kbIds={}, question={}, historySize={}", knowledgeBaseIds, question,
                history != null ? history.size() : 0);
        List<Long> normalizedKnowledgeBaseIds = normalizeKnowledgeBaseIds(knowledgeBaseIds);
        String normalizedQuestion = normalizeQuestion(question);
        List<Message> effectiveHistory = sanitizeHistory(history);

        if (normalizedQuestion.isBlank()) {
            return Flux.just(EMPTY_QUESTION_RESPONSE);
        }

        if (normalizedKnowledgeBaseIds.isEmpty()) {
            return streamGeneralAnswer(normalizedQuestion, effectiveHistory, "当前未选择知识库");
        }

        try {
            // 1. 验证知识库是否存在并更新问题计数
            countService.updateQuestionCounts(normalizedKnowledgeBaseIds);

            // 2. Query rewrite + 动态参数检索
            QueryContext queryContext = buildQueryContext(normalizedQuestion, effectiveHistory);
            List<Document> relevantDocs = retrieveRelevantDocs(queryContext, normalizedKnowledgeBaseIds);

            if (!hasEffectiveHit(relevantDocs)) {
                return streamGeneralAnswer(normalizedQuestion, effectiveHistory, "当前知识库未检索到相关内容");
            }

            // 3. 构建上下文
            String context = relevantDocs.stream()
                    .map(Document::getText)
                    .collect(Collectors.joining("\n\n---\n\n"));

            log.debug("检索到 {} 个相关文档片段", relevantDocs.size());

            // 4. 构建提示词
            String systemPrompt = buildSystemPrompt();
            String userPrompt = buildUserPrompt(context, normalizedQuestion);

            // 5. 流式调用（带历史上下文）+ 探测窗口归一化
            Flux<String> responseFlux = useGatewayCompatibility()
                ? gatewayClient.streamText(providerId, systemPrompt, buildGatewayInput(userPrompt, effectiveHistory))
                : buildDefaultStreamResponse(systemPrompt, userPrompt, effectiveHistory);

            log.info("开始流式输出知识库回答(探测窗口): kbIds={}", normalizedKnowledgeBaseIds);
            return normalizeStreamOutput(responseFlux)
                .doOnComplete(() -> log.info("流式输出完成: kbIds={}", normalizedKnowledgeBaseIds))
                .onErrorResume(e -> {
                    log.error("流式输出失败: kbIds={}, error={}", normalizedKnowledgeBaseIds, e.getMessage(), e);
                    return Flux.just("【错误】知识库查询失败：AI服务暂时不可用，请稍后重试。");
                });

        } catch (Exception e) {
            log.error("知识库流式问答失败: {}", e.getMessage(), e);
            return Flux.just("【错误】知识库查询失败：" + e.getMessage());
        }
    }

    private QueryContext buildQueryContext(String originalQuestion, List<Message> history) {
        String normalizedQuestion = normalizeQuestion(originalQuestion);
        String rewrittenQuestion = rewriteQuestion(normalizedQuestion, history);
        Set<String> candidates = new LinkedHashSet<>();
        candidates.add(rewrittenQuestion);
        candidates.add(normalizedQuestion);

        SearchParams searchParams = resolveSearchParams(normalizedQuestion);
        return new QueryContext(normalizedQuestion, new ArrayList<>(candidates), searchParams);
    }

    private List<Message> sanitizeHistory(List<Message> history) {
        if (history == null || history.isEmpty()) {
            return List.of();
        }
        return history;
    }

    private List<Long> normalizeKnowledgeBaseIds(List<Long> knowledgeBaseIds) {
        return knowledgeBaseIds == null ? List.of() : knowledgeBaseIds;
    }

//       清洗
    private String normalizeQuestion(String question) {
        return question == null ? "" : question.trim();
    }

//    向量检索
    private List<Document> retrieveRelevantDocs(QueryContext queryContext, List<Long> knowledgeBaseIds) {
        for (String candidateQuery : queryContext.candidateQueries()) {
            if (candidateQuery.isBlank()) {
                continue;
            }
            List<Document> docs = vectorService.similaritySearch(
                candidateQuery,
                knowledgeBaseIds,
                queryContext.searchParams().topK(),
                queryContext.searchParams().minScore()
            );
            log.info("检索候选 query='{}'，命中 {} 条", candidateQuery, docs.size());
            if (hasEffectiveHit(docs)) {
                return docs;
            }
        }
        return List.of();
    }

    private SearchParams resolveSearchParams(String question) {
        int compactLength = question.replaceAll("\\s+", "").length();
        if (compactLength <= shortQueryLength) {
            return new SearchParams(topkShort, minScoreShort);
        }
        if (compactLength <= 12) {
            return new SearchParams(topkMedium, minScoreDefault);
        }
        return new SearchParams(topkLong, minScoreDefault);
    }

//    改写
    private String rewriteQuestion(String question, List<Message> history) {
        if (!rewriteEnabled || question.isBlank()) {
            return question;
        }
        try {
            Map<String, Object> variables = new HashMap<>();
            variables.put("question", question);
            variables.put("history", formatHistoryForRewrite(history));
            String rewritePrompt = rewritePromptTemplate.render(variables);
            String rewritten = useGatewayCompatibility()
                ? gatewayClient.generateText(providerId, "", rewritePrompt)
                : chatClient.prompt()
                    .user(rewritePrompt)
                    .call()
                    .content();
            if (rewritten == null || rewritten.isBlank()) {
                return question;
            }
            String normalized = rewritten.trim();
            log.info("Query rewrite: origin='{}', rewritten='{}', historySize={}", question, normalized, history.size());
            return normalized;
        } catch (Exception e) {
            log.warn("Query rewrite 失败，使用原问题继续检索: {}", e.getMessage());
            return question;
        }
    }

    /**
     * 将历史消息格式化为重写 prompt 中的文本摘要。
     * 每条消息格式：用户: xxx / 助手: xxx
     */
    private String formatHistoryForRewrite(List<Message> history) {
        if (history == null || history.isEmpty()) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        for (Message msg : history) {
            if (msg instanceof UserMessage) {
                sb.append("用户: ").append(msg.getText()).append("\n");
            } else if (msg instanceof AssistantMessage) {
                // 截断过长的助手回复，避免 rewrite prompt 过长
                String text = msg.getText();
                if (text.length() > MAX_REWRITE_HISTORY_CHAR) {
                    text = text.substring(0, MAX_REWRITE_HISTORY_CHAR) + "...";
                }
                sb.append("助手: ").append(text).append("\n");
            }
        }
        return sb.toString().trim();
    }

    private boolean useGatewayCompatibility() {
        return gatewayClient.supports(providerId);
    }

    private String answerWithoutKnowledgeBase(String question, String reason) {
        String userPrompt = buildGeneralAnswerPrompt(question, reason);
        try {
            String answer = useGatewayCompatibility()
                ? gatewayClient.generateText(providerId, GENERAL_ANSWER_SYSTEM_PROMPT, userPrompt)
                : chatClient.prompt()
                    .system(GENERAL_ANSWER_SYSTEM_PROMPT)
                    .user(userPrompt)
                    .call()
                    .content();
            return normalizeGeneralAnswer(answer, reason);
        } catch (Exception e) {
            log.error("通用问答失败: reason={}, error={}", reason, e.getMessage(), e);
            throw new BusinessException(ErrorCode.KNOWLEDGE_BASE_QUERY_FAILED, "知识库查询失败：" + e.getMessage());
        }
    }

    private Flux<String> streamGeneralAnswer(String question, List<Message> history, String reason) {
        String userPrompt = buildGeneralAnswerPrompt(question, reason);
        Flux<String> responseFlux = useGatewayCompatibility()
            ? gatewayClient.streamText(providerId, GENERAL_ANSWER_SYSTEM_PROMPT, buildGatewayInput(userPrompt, history))
            : buildDefaultStreamResponse(GENERAL_ANSWER_SYSTEM_PROMPT, userPrompt, history);

        return responseFlux.switchIfEmpty(Flux.just(reason + "，暂时无法生成回答，请稍后重试。"));
    }

    private String buildGeneralAnswerPrompt(String question, String reason) {
        return """
            当前状态：%s。

            请先用一句中文自然说明这一点，然后继续回答下面的问题：
            %s
            """.formatted(reason, question);
    }

    private Flux<String> buildDefaultStreamResponse(String systemPrompt,
                                                    String userPrompt,
                                                    List<Message> effectiveHistory) {
        var promptSpec = chatClient.prompt().system(systemPrompt);
        if (!effectiveHistory.isEmpty()) {
            promptSpec = promptSpec.messages(effectiveHistory);
        }
        return promptSpec.user(userPrompt).stream().content();
    }

    private String buildGatewayInput(String userPrompt, List<Message> effectiveHistory) {
        if (effectiveHistory == null || effectiveHistory.isEmpty()) {
            return userPrompt;
        }
        String historyText = formatHistoryForRewrite(effectiveHistory);
        return historyText.isBlank() ? userPrompt : historyText + "\n\n当前请求:\n" + userPrompt;
    }

    private boolean hasEffectiveHit(List<Document> docs) {
        return docs != null && !docs.isEmpty();
    }

    private String normalizeGeneralAnswer(String answer, String reason) {
        if (answer == null || answer.isBlank()) {
            return reason + "，暂时无法生成回答，请稍后重试。";
        }
        return answer.trim();
    }

    private String normalizeAnswer(String answer) {
        if (answer == null || answer.isBlank()) {
            return NO_RESULT_RESPONSE;
        }
        String normalized = answer.trim();
        if (isNoResultLike(normalized)) {
            return NO_RESULT_RESPONSE;
        }
        return normalized;
    }

    private boolean isNoResultLike(String text) {
        return text.contains("没有找到相关信息")
            || text.contains("未检索到相关信息")
            || text.contains("信息不足")
            || text.contains("超出知识库范围")
            || text.contains("无法根据提供内容回答");
    }

    /**
     * 先观察前一小段流式内容，快速识别“无信息”模板。
     * - 命中无信息：立即输出固定模板并结束，防止长篇拒答
     * - 非无信息：尽快释放缓冲并继续实时透传
     */
    private Flux<String> normalizeStreamOutput(Flux<String> rawFlux) {
        return Flux.create(sink -> {
            StringBuilder probeBuffer = new StringBuilder();
            AtomicBoolean passthrough = new AtomicBoolean(false);
            AtomicBoolean completed = new AtomicBoolean(false);
            final Disposable[] disposableRef = new Disposable[1];

            disposableRef[0] = rawFlux.subscribe(
                chunk -> {
                    if (completed.get() || sink.isCancelled()) {
                        return;
                    }
                    if (passthrough.get()) {
                        sink.next(chunk);
                        return;
                    }

                    probeBuffer.append(chunk);
                    String probeText = probeBuffer.toString();
                    if (isNoResultLike(probeText)) {
                        completed.set(true);
                        sink.next(NO_RESULT_RESPONSE);
                        sink.complete();
                        if (disposableRef[0] != null) {
                            disposableRef[0].dispose();
                        }
                        return;
                    }

                    if (probeBuffer.length() >= STREAM_PROBE_CHARS) {
                        passthrough.set(true);
                        sink.next(probeText);
                        probeBuffer.setLength(0);
                    }
                },
                sink::error,
                () -> {
                    if (completed.get() || sink.isCancelled()) {
                        return;
                    }
                    if (!passthrough.get()) {
                        sink.next(normalizeAnswer(probeBuffer.toString()));
                    }
                    sink.complete();
                }
            );

            sink.onCancel(() -> {
                if (disposableRef[0] != null) {
                    disposableRef[0].dispose();
                }
            });
        });
    }

    private record SearchParams(int topK, double minScore) {
    }

    private record QueryContext(String originalQuestion, List<String> candidateQueries, SearchParams searchParams) {
    }
}
