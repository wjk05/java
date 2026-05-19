package com.ruici.ai.common.ai;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("LLM Provider 注册表测试")
class LlmProviderRegistryTest {

    @Nested
    @DisplayName("OpenAI API Base URL 规范化")
    class OpenAiApiBaseUrlNormalization {

        @Test
        @DisplayName("baseUrl 已含 /v1 时移除，避免 Spring AI 拼出 /v1/v1")
        void shouldRemoveTrailingV1Path() {
            String normalized = LlmProviderRegistry.normalizeBaseUrlForOpenAiApi(
                "https://api-s.zwenooo.link/v1"
            );

            assertThat(normalized).isEqualTo("https://api-s.zwenooo.link");
        }

        @Test
        @DisplayName("baseUrl 含业务路径和 /v1 时只移除版本段")
        void shouldKeepGatewayPathBeforeTrailingV1() {
            String normalized = LlmProviderRegistry.normalizeBaseUrlForOpenAiApi(
                "https://dashscope.aliyuncs.com/compatible-mode/v1/"
            );

            assertThat(normalized).isEqualTo("https://dashscope.aliyuncs.com/compatible-mode");
        }

        @Test
        @DisplayName("baseUrl 无 /v1 时保持原路径")
        void shouldKeepBaseUrlWithoutVersionPath() {
            String normalized = LlmProviderRegistry.normalizeBaseUrlForOpenAiApi(
                "http://localhost:1234"
            );

            assertThat(normalized).isEqualTo("http://localhost:1234");
        }

        @Test
        @DisplayName("baseUrl 为空时抛出配置异常")
        void shouldRejectBlankBaseUrl() {
            assertThatThrownBy(() -> LlmProviderRegistry.normalizeBaseUrlForOpenAiApi(" "))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Provider baseUrl must not be blank");
        }
    }
}
