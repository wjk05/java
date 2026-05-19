package com.ruici.ai.common.ai;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("OpenAI Compatible 响应提取测试")
class OpenAiCompatibleResponseExtractorTest {

    private final OpenAiCompatibleResponseExtractor extractor =
        new OpenAiCompatibleResponseExtractor(new ObjectMapper());

    @Nested
    @DisplayName("Responses API 兼容提取")
    class ResponsesApi {

        @Test
        @DisplayName("可以提取 output_text delta 事件")
        void shouldExtractResponsesDelta() {
            String payload = "data: {\"type\":\"response.output_text.delta\",\"delta\":\"你好\"}\n\n"
                + "data: {\"type\":\"response.output_text.delta\",\"delta\":\"，世界\"}\n\n"
                + "data: [DONE]";

            String text = extractor.collectResponsesText(List.of(payload));

            assertThat(text).isEqualTo("你好，世界");
        }

        @Test
        @DisplayName("当只有完成态正文时回退提取 output.content.text")
        void shouldFallbackToCompletedResponseText() {
            String payload = "data: {\"type\":\"response.completed\",\"response\":{\"output\":[{\"content\":[{\"text\":\"完整回答\"}]}]}}";

            String text = extractor.collectResponsesText(List.of(payload));

            assertThat(text).isEqualTo("完整回答");
        }
    }

    @Nested
    @DisplayName("Chat Completions 兼容提取")
    class ChatCompletions {

        @Test
        @DisplayName("可以提取 chat completions 流式 delta")
        void shouldExtractChatCompletionDelta() {
            String payload = "data: {\"choices\":[{\"delta\":{\"content\":\"你好\"}}]}\n\n"
                + "data: {\"choices\":[{\"delta\":{\"content\":\"，中转\"}}]}\n\n"
                + "data: [DONE]";

            List<String> chunks = extractor.extractChatCompletionsStreamTexts(payload, true);

            assertThat(chunks).containsExactly("你好", "，中转");
        }

        @Test
        @DisplayName("可以提取 chat completions 非流式 message.content")
        void shouldExtractChatCompletionContent() {
            String payload = "{\"choices\":[{\"message\":{\"content\":\"普通完成响应\"}}]}";

            String text = extractor.collectChatCompletionsText(payload);

            assertThat(text).isEqualTo("普通完成响应");
        }
    }
}
