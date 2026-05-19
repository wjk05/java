package com.ruici.ai.common.ai;

import com.ruici.ai.common.config.LlmProviderProperties;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("OpenAI Compatible 网关端点测试")
class OpenAiCompatibleGatewayClientTest {

    private final OpenAiCompatibleGatewayClient gatewayClient = new OpenAiCompatibleGatewayClient(
        new LlmProviderProperties(),
        new OpenAiCompatibleResponseExtractor(new com.fasterxml.jackson.databind.ObjectMapper())
    );

    @Nested
    @DisplayName("端点候选构造")
    class EndpointCandidates {

        @Test
        @DisplayName("baseUrl 无路径时优先补 /v1")
        void shouldPreferV1WhenBaseUrlHasNoPath() {
            List<String> endpoints = gatewayClient.buildEndpointCandidates(
                "https://api-s.zwenooo.link",
                "/chat/completions"
            );

            assertThat(endpoints).containsExactly(
                "https://api-s.zwenooo.link/v1/chat/completions",
                "https://api-s.zwenooo.link/chat/completions"
            );
        }

        @Test
        @DisplayName("baseUrl 已含 /v1 时保持原路径")
        void shouldKeepExistingVersionPath() {
            List<String> endpoints = gatewayClient.buildEndpointCandidates(
                "https://api-s.zwenooo.link/v1",
                "/responses"
            );

            assertThat(endpoints).containsExactly(
                "https://api-s.zwenooo.link/v1/responses"
            );
        }

        @Test
        @DisplayName("模型候选包含命名变体与备用模型")
        void shouldBuildModelCandidatesWithFallbackAndAlias() {
            LlmProviderProperties.ProviderConfig config = new LlmProviderProperties.ProviderConfig();
            config.setModel("gpt-5.2");

            List<String> models = gatewayClient.buildModelCandidates(config);

            assertThat(models).containsExactly("gpt-5.2", "gpt5.2");
        }
    }
}
