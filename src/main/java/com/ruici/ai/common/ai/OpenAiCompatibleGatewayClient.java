package com.ruici.ai.common.ai;

import com.ruici.ai.common.config.LlmProviderProperties;
import com.ruici.ai.common.config.LlmProviderProperties.ProviderConfig;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.util.UriComponentsBuilder;
import reactor.core.publisher.Flux;

import java.net.URI;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

@Component
@Slf4j
public class OpenAiCompatibleGatewayClient {

    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(120);

    private final LlmProviderProperties providerProperties;
    private final OpenAiCompatibleResponseExtractor responseExtractor;

    public OpenAiCompatibleGatewayClient(LlmProviderProperties providerProperties,
                                         OpenAiCompatibleResponseExtractor responseExtractor) {
        this.providerProperties = providerProperties;
        this.responseExtractor = responseExtractor;
    }

    public boolean supports(String providerId) {
        return "third-party".equals(resolveProviderId(providerId));
    }

    public String generateText(String providerId, String instructions, String input) {
        ProviderConfig config = getProviderConfig(providerId);
        List<String> modelCandidates = buildModelCandidates(config);

        Exception responsesFailure = null;
        for (String model : modelCandidates) {
            for (String endpoint : buildEndpointCandidates(config.getBaseUrl(), "/responses")) {
                try {
                    List<String> payloads = requestResponses(config, endpoint, model, instructions, input)
                        .collectList()
                        .block(REQUEST_TIMEOUT);
                    String text = responseExtractor.collectResponsesText(payloads != null ? payloads : List.of());
                    if (StringUtils.hasText(text)) {
                        return text;
                    }
                }
                catch (Exception e) {
                    responsesFailure = e;
                    log.warn("Responses API 调用失败，准备尝试下一个候选组合: provider={}, model={}, endpoint={}, error={}",
                        resolveProviderId(providerId), model, endpoint, e.getMessage());
                }
            }
        }

        if (responsesFailure != null) {
            log.warn("Responses API 全部候选端点失败，回退到 chat/completions: provider={}, error={}",
                resolveProviderId(providerId), responsesFailure.getMessage());
        }

        return requestChatCompletionWithFallback(config, modelCandidates, instructions, input, false);
    }

    public Flux<String> streamText(String providerId, String instructions, String input) {
        ProviderConfig config = getProviderConfig(providerId);
        List<String> modelCandidates = buildModelCandidates(config);

        return requestResponsesWithFallback(config, modelCandidates, instructions, input)
            .transform(this::decodeResponsesStream)
            .switchIfEmpty(decodeChatCompletionsStreamWithFallback(config, modelCandidates, instructions, input))
            .onErrorResume(error -> {
                log.warn("Responses SSE 调用失败，回退到 chat/completions 流式: provider={}, error={}",
                    resolveProviderId(providerId), error.getMessage());
                return decodeChatCompletionsStreamWithFallback(config, modelCandidates, instructions, input);
            });
    }

    private Flux<String> decodeResponsesStream(Flux<String> payloadFlux) {
        AtomicBoolean emitted = new AtomicBoolean(false);
        return payloadFlux.flatMapIterable(payload -> {
            List<String> chunks = responseExtractor.extractResponsesStreamTexts(payload, !emitted.get());
            if (!chunks.isEmpty()) {
                emitted.set(true);
            }
            return chunks;
        });
    }

    private Flux<String> decodeChatCompletionsStream(ProviderConfig config,
                                                     String endpoint,
                                                     String model,
                                                     String instructions,
                                                     String input) {
        AtomicBoolean emitted = new AtomicBoolean(false);
        return requestChatCompletionsStream(config, endpoint, model, instructions, input)
            .flatMapIterable(payload -> {
                List<String> chunks = responseExtractor.extractChatCompletionsStreamTexts(payload, !emitted.get());
                if (!chunks.isEmpty()) {
                    emitted.set(true);
                }
                return chunks;
            });
    }

    private Flux<String> requestResponses(ProviderConfig config,
                                          String endpoint,
                                          String model,
                                          String instructions,
                                          String input) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", model);
        if (StringUtils.hasText(instructions)) {
            body.put("instructions", instructions);
        }
        body.put("input", input);
        body.put("stream", true);

        return createWebClient(config).post()
            .uri(endpoint)
            .contentType(MediaType.APPLICATION_JSON)
            .accept(MediaType.TEXT_EVENT_STREAM, MediaType.APPLICATION_JSON)
            .bodyValue(body)
            .retrieve()
            .bodyToFlux(String.class);
    }

    private String requestChatCompletion(ProviderConfig config,
                                         String endpoint,
                                         String model,
                                         String instructions,
                                         String input,
                                         boolean stream) {
        return createRestClient(config).post()
            .uri(endpoint)
            .contentType(MediaType.APPLICATION_JSON)
            .body(buildChatCompletionsBody(model, instructions, input, stream))
            .retrieve()
            .body(String.class);
    }

    private Flux<String> requestChatCompletionsStream(ProviderConfig config,
                                                      String endpoint,
                                                      String model,
                                                      String instructions,
                                                      String input) {
        return createWebClient(config).post()
            .uri(endpoint)
            .contentType(MediaType.APPLICATION_JSON)
            .accept(MediaType.TEXT_EVENT_STREAM, MediaType.APPLICATION_JSON)
            .bodyValue(buildChatCompletionsBody(model, instructions, input, true))
            .retrieve()
            .bodyToFlux(String.class);
    }

    private Map<String, Object> buildChatCompletionsBody(String model,
                                                          String instructions,
                                                          String input,
                                                          boolean stream) {
        List<Map<String, String>> messages = new ArrayList<>();
        if (StringUtils.hasText(instructions)) {
            messages.add(Map.of("role", "system", "content", instructions));
        }
        messages.add(Map.of("role", "user", "content", input));

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", model);
        body.put("messages", messages);
        body.put("stream", stream);
        return body;
    }

    private RestClient createRestClient(ProviderConfig config) {
        return RestClient.builder()
            .defaultHeader(HttpHeaders.AUTHORIZATION, bearerToken(config.getApiKey()))
            .build();
    }

    private WebClient createWebClient(ProviderConfig config) {
        return WebClient.builder()
            .defaultHeader(HttpHeaders.AUTHORIZATION, bearerToken(config.getApiKey()))
            .build();
    }

    private ProviderConfig getProviderConfig(String providerId) {
        String resolvedProviderId = resolveProviderId(providerId);
        ProviderConfig config = providerProperties.getProviders().get(resolvedProviderId);
        if (config == null) {
            throw new IllegalArgumentException("Unknown LLM provider: " + resolvedProviderId);
        }
        return config;
    }

    private String resolveProviderId(String providerId) {
        return StringUtils.hasText(providerId) ? providerId : providerProperties.getDefaultProvider();
    }

    List<String> buildEndpointCandidates(String baseUrl, String apiPath) {
        if (!StringUtils.hasText(apiPath)) {
            return List.of();
        }

        String normalizedBaseUrl = normalizeBaseUrl(baseUrl);
        URI baseUri = UriComponentsBuilder.fromUriString(normalizedBaseUrl).build(true).toUri();
        String normalizedApiPath = apiPath.startsWith("/") ? apiPath : "/" + apiPath;
        Set<String> candidates = new LinkedHashSet<>();

        String existingPath = baseUri.getPath();
        if (StringUtils.hasText(existingPath) && !"/".equals(existingPath)) {
            candidates.add(UriComponentsBuilder.fromUri(baseUri)
                .replacePath(joinPaths(existingPath, normalizedApiPath))
                .build(true)
                .toUriString());
        }
        else {
            candidates.add(UriComponentsBuilder.fromUri(baseUri)
                .replacePath(joinPaths("/v1", normalizedApiPath))
                .build(true)
                .toUriString());
            candidates.add(UriComponentsBuilder.fromUri(baseUri)
                .replacePath(normalizedApiPath)
                .build(true)
                .toUriString());
        }
        return new ArrayList<>(candidates);
    }

    private String normalizeBaseUrl(String baseUrl) {
        if (!StringUtils.hasText(baseUrl)) {
            throw new IllegalArgumentException("Provider baseUrl must not be blank");
        }
        return baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
    }

    private String requestChatCompletionWithFallback(ProviderConfig config,
                                                     List<String> modelCandidates,
                                                     String instructions,
                                                     String input,
                                                     boolean stream) {
        Exception lastFailure = null;
        for (String model : modelCandidates) {
            for (String endpoint : buildEndpointCandidates(config.getBaseUrl(), "/chat/completions")) {
                try {
                    String payload = requestChatCompletion(config, endpoint, model, instructions, input, stream);
                    String text = responseExtractor.collectChatCompletionsText(payload);
                    if (StringUtils.hasText(text)) {
                        return text;
                    }
                }
                catch (Exception e) {
                    lastFailure = e;
                    log.warn("Chat Completions 调用失败，准备尝试下一个候选组合: model={}, endpoint={}, error={}",
                        model, endpoint, e.getMessage());
                }
            }
        }
        if (lastFailure instanceof RuntimeException runtimeException) {
            throw runtimeException;
        }
        throw new IllegalStateException("Chat Completions 调用失败", lastFailure);
    }

    private Flux<String> requestResponsesWithFallback(ProviderConfig config,
                                                      List<String> modelCandidates,
                                                      String instructions,
                                                      String input) {
        return requestResponsesWithFallback(config,
            modelCandidates,
            buildEndpointCandidates(config.getBaseUrl(), "/responses"),
            0,
            0,
            instructions,
            input);
    }

    private Flux<String> decodeChatCompletionsStreamWithFallback(ProviderConfig config,
                                                                 List<String> modelCandidates,
                                                                 String instructions,
                                                                 String input) {
        return decodeChatCompletionsStreamWithFallback(config,
            modelCandidates,
            buildEndpointCandidates(config.getBaseUrl(), "/chat/completions"),
            0,
            0,
            instructions,
            input);
    }

    private String joinPaths(String basePath, String apiPath) {
        String normalizedBasePath = basePath.endsWith("/")
            ? basePath.substring(0, basePath.length() - 1)
            : basePath;
        String normalizedApiPath = apiPath.startsWith("/") ? apiPath : "/" + apiPath;
        return normalizedBasePath + normalizedApiPath;
    }

    List<String> buildModelCandidates(ProviderConfig config) {
        LinkedHashSet<String> candidates = new LinkedHashSet<>();
        if (StringUtils.hasText(config.getModel())) {
            addModelCandidateVariants(candidates, config.getModel());
        }
        if (StringUtils.hasText(config.getFallbackModel())) {
            addModelCandidateVariants(candidates, config.getFallbackModel());
        }
        return new ArrayList<>(candidates);
    }

    private void addModelCandidateVariants(Set<String> candidates, String model) {
        String normalized = model.trim();
        if (normalized.isBlank()) {
            return;
        }
        candidates.add(normalized);
        if (normalized.matches(".*\\d.*")
            && normalized.contains("gpt")
            && normalized.contains(".")
            && !normalized.startsWith("gpt-")) {
            candidates.add(normalized.replace("gpt", "gpt-") );
        }
        if (normalized.startsWith("gpt-") && normalized.contains(".")) {
            candidates.add(normalized.replaceFirst("gpt-", "gpt"));
        }
    }

    private Flux<String> requestResponsesWithFallback(ProviderConfig config,
                                                      List<String> modelCandidates,
                                                      List<String> endpoints,
                                                      int modelIndex,
                                                      int index,
                                                      String instructions,
                                                      String input) {
        if (modelIndex >= modelCandidates.size()) {
            return Flux.error(new IllegalStateException("All /responses endpoints failed"));
        }
        if (index >= endpoints.size()) {
            return requestResponsesWithFallback(config, modelCandidates, endpoints, modelIndex + 1, 0,
                instructions, input);
        }
        String model = modelCandidates.get(modelIndex);
        String endpoint = endpoints.get(index);
        return requestResponses(config, endpoint, model, instructions, input)
            .onErrorResume(error -> {
                log.warn("Responses SSE 组合失败: model={}, endpoint={}, error={}",
                    model, endpoint, error.getMessage());
                return requestResponsesWithFallback(config, modelCandidates, endpoints, modelIndex, index + 1,
                    instructions, input);
            });
    }

    private Flux<String> decodeChatCompletionsStreamWithFallback(ProviderConfig config,
                                                                 List<String> modelCandidates,
                                                                 List<String> endpoints,
                                                                 int modelIndex,
                                                                 int index,
                                                                 String instructions,
                                                                 String input) {
        if (modelIndex >= modelCandidates.size()) {
            return Flux.error(new IllegalStateException("All chat/completions endpoints failed"));
        }
        if (index >= endpoints.size()) {
            return decodeChatCompletionsStreamWithFallback(config, modelCandidates, endpoints, modelIndex + 1, 0,
                instructions, input);
        }
        String model = modelCandidates.get(modelIndex);
        String endpoint = endpoints.get(index);
        return decodeChatCompletionsStream(config, endpoint, model, instructions, input)
            .onErrorResume(error -> {
                log.warn("Chat Completions 流式组合失败: model={}, endpoint={}, error={}",
                    model, endpoint, error.getMessage());
                return decodeChatCompletionsStreamWithFallback(config, modelCandidates, endpoints, modelIndex,
                    index + 1, instructions, input);
            });
    }

    private String bearerToken(String apiKey) {
        return "Bearer " + (apiKey != null ? apiKey : "");
    }
}
