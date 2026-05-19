package com.ruici.ai.common.ai;

import com.ruici.ai.common.config.LlmProviderProperties;
import com.ruici.ai.common.config.LlmProviderProperties.AdvisorConfig;
import com.ruici.ai.common.config.LlmProviderProperties.ProviderConfig;
import io.micrometer.observation.ObservationRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.client.advisor.SimpleLoggerAdvisor;
import org.springframework.ai.chat.client.advisor.ToolCallAdvisor;
import org.springframework.ai.chat.client.advisor.api.Advisor;
import org.springframework.ai.chat.memory.MessageWindowChatMemory;
import org.springframework.ai.model.tool.ToolCallingManager;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.openai.api.OpenAiApi;
import org.springframework.ai.retry.RetryUtils;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Registry for managing and caching LLM providers.
 * Supports dynamic creation of ChatClient based on provider configurations.
 */
@Component
@Slf4j
public class LlmProviderRegistry {

    private final LlmProviderProperties properties;
    private final Map<String, ChatClient> clientCache = new ConcurrentHashMap<>();

    private final ToolCallingManager toolCallingManager;
    private final ObservationRegistry observationRegistry;
    private final ToolCallback interviewSkillsToolCallback;

    public LlmProviderRegistry(
            LlmProviderProperties properties,
            @Autowired(required = false) ToolCallingManager toolCallingManager,
            @Autowired(required = false) ObservationRegistry observationRegistry,
            @Autowired(required = false) @Qualifier("interviewSkillsToolCallback") ToolCallback interviewSkillsToolCallback) {
        this.properties = properties;
        this.toolCallingManager = toolCallingManager;
        this.observationRegistry = observationRegistry;
        this.interviewSkillsToolCallback = interviewSkillsToolCallback;
    }

    /**
     * Get a ChatClient for the specified provider ID.
     * If the client is not in the cache, it will be created based on the provider's configuration.
     *
     * @param providerId The ID of the provider (e.g., "dashscope", "lmstudio")
     * @return A ChatClient instance
     * @throws IllegalArgumentException if the providerId is unknown
     */
    public ChatClient getChatClient(String providerId) {
        log.info("[LlmProviderRegistry] Requesting client for provider: {}", providerId);
        return clientCache.computeIfAbsent(providerId, id -> {
            log.info("[LlmProviderRegistry] Cache miss. Creating new client for: {}", id);
            return createChatClient(id);
        });
    }

    /**
     * Get the default ChatClient based on app.ai.default-provider.
     *
     * @return The default ChatClient instance
     */
    public ChatClient getDefaultChatClient() {
        return getChatClient(properties.getDefaultProvider());
    }

    /**
     * Get a ChatClient for the specified provider, falling back to the default if null or blank.
     */
    public ChatClient getChatClientOrDefault(String providerId) {
        return (providerId != null && !providerId.isBlank())
            ? getChatClient(providerId)
            : getDefaultChatClient();
    }

    /**
     * 获取不带 SkillsTool 的 ChatClient，用于简历题生成等不需要 Agent 工具调用的场景。
     */
    public ChatClient getPlainChatClient(String providerId) {
        String id = resolveProviderId(providerId);
        return clientCache.computeIfAbsent(id + ":plain", key -> createPlainChatClient(id));
    }

    /**
     * 获取语音交互专用 ChatClient。
     *
     * <p>语音链路对首包时延和稳定性更敏感，这里显式禁用 SkillsTool / ToolCallAdvisor，
     * 避免 OpenAI-compatible provider 在流式回复中触发工具调用导致链路中断。</p>
     */
    public ChatClient getVoiceChatClient(String providerId) {
        String id = resolveProviderId(providerId);
        return clientCache.computeIfAbsent(id + ":voice", key -> createVoiceChatClient(id));
    }

    private ChatClient createChatClient(String providerId) {
        OpenAiChatModel chatModel = buildChatModel(providerId);

        ChatClient.Builder builder = ChatClient.builder(chatModel);
        if (interviewSkillsToolCallback != null) {
            builder.defaultToolCallbacks(interviewSkillsToolCallback);
        }
        List<Advisor> advisors = buildDefaultAdvisors(providerId);
        if (!advisors.isEmpty()) {
            builder.defaultAdvisors(advisors.toArray(new Advisor[0]));
            log.info("[LlmProviderRegistry] Applied {} advisors for provider {}", advisors.size(), providerId);
        }

        return builder.build();
    }

    private ChatClient createPlainChatClient(String providerId) {
        OpenAiChatModel chatModel = buildChatModel(providerId);
        log.info("[LlmProviderRegistry] Created plain ChatClient (no tools) for {}", providerId);
        return ChatClient.builder(chatModel).build();
    }

    private ChatClient createVoiceChatClient(String providerId) {
        OpenAiChatModel chatModel = buildChatModel(providerId);
        log.info("[LlmProviderRegistry] Created voice ChatClient (plain/no tools) for {}", providerId);
        return ChatClient.builder(chatModel).build();
    }

    private OpenAiChatModel buildChatModel(String providerId) {
        ProviderConfig config = properties.getProviders().get(providerId);
        if (config == null) {
            log.error("[LlmProviderRegistry] Provider config not found: {}", providerId);
            throw new IllegalArgumentException("Unknown LLM provider: " + providerId);
        }

        String normalizedBaseUrl = normalizeBaseUrlForOpenAiApi(config.getBaseUrl());

        log.info("[LlmProviderRegistry] Building ChatModel - Provider: {}, BaseUrl: {}, Model: {}",
                 providerId, normalizedBaseUrl, config.getModel());

        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(10000);
        requestFactory.setReadTimeout(300000);

        RestClient.Builder restClientBuilder = RestClient.builder()
                .requestFactory(requestFactory);

        OpenAiApi openAiApi = OpenAiApi.builder()
                .baseUrl(normalizedBaseUrl)
                .apiKey(config.getApiKey())
                .restClientBuilder(restClientBuilder)
                .build();

        OpenAiChatOptions options = OpenAiChatOptions.builder()
                .model(config.getModel())
                .temperature(0.2)
                .build();

        return new OpenAiChatModel(
                openAiApi,
                options,
                toolCallingManager,
                RetryUtils.DEFAULT_RETRY_TEMPLATE,
                observationRegistry != null ? observationRegistry : ObservationRegistry.NOOP
        );
    }

    private List<Advisor> buildDefaultAdvisors(String providerId) {
        AdvisorConfig config = properties.getAdvisors();
        if (config == null || !config.isEnabled()) {
            return List.of();
        }

        List<Advisor> advisors = new ArrayList<>();

        if (config.isToolCallEnabled()) {
            if (toolCallingManager != null) {
                advisors.add(buildToolCallAdvisor(
                    config.isToolCallConversationHistoryEnabled(),
                    config.isStreamToolCallResponses()));
            } else {
                log.warn("[LlmProviderRegistry] ToolCallAdvisor skipped: ToolCallingManager unavailable, provider={}", providerId);
            }
        }

        if (config.isMessageChatMemoryEnabled()) {
            int maxMessages = Math.max(20, config.getMessageChatMemoryMaxMessages());
            MessageChatMemoryAdvisor memoryAdvisor = MessageChatMemoryAdvisor.builder(
                MessageWindowChatMemory.builder()
                    .maxMessages(maxMessages)
                    .build()
            ).build();
            advisors.add(memoryAdvisor);
        }

        if (config.isSimpleLoggerEnabled()) {
            advisors.add(new SimpleLoggerAdvisor());
        }

        return advisors;
    }

    private ToolCallAdvisor buildToolCallAdvisor(boolean conversationHistoryEnabled,
                                                  boolean streamToolCallResponses) {
        return ToolCallAdvisor.builder()
            .toolCallingManager(toolCallingManager)
            .conversationHistoryEnabled(conversationHistoryEnabled)
            .streamToolCallResponses(streamToolCallResponses)
            .build();
    }

    private String resolveProviderId(String providerId) {
        return (providerId != null && !providerId.isBlank())
            ? providerId : properties.getDefaultProvider();
    }

    static String normalizeBaseUrlForOpenAiApi(String baseUrl) {
        if (baseUrl == null || baseUrl.isBlank()) {
            throw new IllegalArgumentException("Provider baseUrl must not be blank");
        }

        String normalized = baseUrl.trim();
        while (normalized.endsWith("/")) {
            normalized = normalized.substring(0, normalized.length() - 1);
        }

        if (normalized.endsWith("/v1")) {
            return normalized.substring(0, normalized.length() - "/v1".length());
        }
        return normalized;
    }
}
