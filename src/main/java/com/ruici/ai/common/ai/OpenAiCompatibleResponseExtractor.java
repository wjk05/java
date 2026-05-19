package com.ruici.ai.common.ai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

@Component
public class OpenAiCompatibleResponseExtractor {

    private final ObjectMapper objectMapper;

    public OpenAiCompatibleResponseExtractor(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public String collectResponsesText(List<String> payloads) {
        StringBuilder deltaText = new StringBuilder();
        String fallbackText = "";
        for (String payload : payloads) {
            for (String dataLine : extractDataLines(payload)) {
                if ("[DONE]".equals(dataLine)) {
                    continue;
                }
                JsonNode root = readTree(dataLine);
                if (root == null) {
                    continue;
                }
                String type = root.path("type").asText("");
                String delta = extractResponsesDelta(type, root);
                if (!delta.isBlank()) {
                    deltaText.append(delta);
                    continue;
                }
                String candidate = extractGenericText(root);
                if (!candidate.isBlank()) {
                    fallbackText = candidate;
                }
            }
        }
        return !deltaText.isEmpty() ? deltaText.toString().trim() : fallbackText.trim();
    }

    public List<String> extractResponsesStreamTexts(String payload, boolean allowFullTextFallback) {
        List<String> chunks = new ArrayList<>();
        for (String dataLine : extractDataLines(payload)) {
            if ("[DONE]".equals(dataLine)) {
                continue;
            }
            JsonNode root = readTree(dataLine);
            if (root == null) {
                continue;
            }
            String delta = extractResponsesDelta(root.path("type").asText(""), root);
            if (!delta.isBlank()) {
                chunks.add(delta);
                continue;
            }
            if (allowFullTextFallback) {
                String candidate = extractGenericText(root);
                if (!candidate.isBlank()) {
                    chunks.add(candidate);
                }
            }
        }
        return chunks;
    }

    public String collectChatCompletionsText(String payload) {
        JsonNode root = readTree(payload);
        if (root == null) {
            return "";
        }
        JsonNode choices = root.path("choices");
        if (choices.isArray() && !choices.isEmpty()) {
            JsonNode message = choices.get(0).path("message");
            String content = readContentNode(message.path("content"));
            if (!content.isBlank()) {
                return content.trim();
            }
        }
        return extractGenericText(root).trim();
    }

    public List<String> extractChatCompletionsStreamTexts(String payload, boolean allowFullTextFallback) {
        List<String> chunks = new ArrayList<>();
        for (String dataLine : extractDataLines(payload)) {
            if ("[DONE]".equals(dataLine)) {
                continue;
            }
            JsonNode root = readTree(dataLine);
            if (root == null) {
                continue;
            }
            JsonNode choices = root.path("choices");
            if (choices.isArray() && !choices.isEmpty()) {
                JsonNode deltaNode = choices.get(0).path("delta");
                String content = readContentNode(deltaNode.path("content"));
                if (!content.isBlank()) {
                    chunks.add(content);
                    continue;
                }
            }
            if (allowFullTextFallback) {
                String candidate = extractGenericText(root);
                if (!candidate.isBlank()) {
                    chunks.add(candidate);
                }
            }
        }
        return chunks;
    }

    private List<String> extractDataLines(String payload) {
        List<String> lines = new ArrayList<>();
        if (payload == null || payload.isBlank()) {
            return lines;
        }
        String[] splitLines = payload.split("\\r?\\n");
        for (String line : splitLines) {
            String trimmed = line.trim();
            if (trimmed.startsWith("data:")) {
                String data = trimmed.substring(5).trim();
                if (!data.isBlank()) {
                    lines.add(data);
                }
                continue;
            }
            if (trimmed.startsWith("{")) {
                lines.add(trimmed);
            }
        }
        return lines;
    }

    private JsonNode readTree(String payload) {
        try {
            return objectMapper.readTree(payload);
        }
        catch (IOException ignored) {
            return null;
        }
    }

    private String extractResponsesDelta(String type, JsonNode root) {
        if ("response.output_text.delta".equals(type)) {
            return root.path("delta").asText("");
        }
        if ("response.output_text.done".equals(type)) {
            return root.path("text").asText("");
        }
        if ("response.content_part.done".equals(type)) {
            String partText = root.path("part").path("text").asText("");
            if (!partText.isBlank()) {
                return partText;
            }
            return root.path("text").asText("");
        }
        return "";
    }

    private String extractGenericText(JsonNode root) {
        if (root == null || root.isMissingNode() || root.isNull()) {
            return "";
        }
        String directOutputText = root.path("output_text").asText("");
        if (!directOutputText.isBlank()) {
            return directOutputText;
        }

        String responseOutputText = root.path("response").path("output_text").asText("");
        if (!responseOutputText.isBlank()) {
            return responseOutputText;
        }

        JsonNode outputArray = root.path("output");
        if ((!outputArray.isArray() || outputArray.isEmpty())
            && root.path("response").path("output").isArray()) {
            outputArray = root.path("response").path("output");
        }
        if (outputArray.isArray()) {
            StringBuilder text = new StringBuilder();
            for (JsonNode outputNode : outputArray) {
                JsonNode contentArray = outputNode.path("content");
                if (!contentArray.isArray()) {
                    continue;
                }
                for (JsonNode contentNode : contentArray) {
                    String itemText = contentNode.path("text").asText("");
                    if (!itemText.isBlank()) {
                        text.append(itemText);
                    }
                }
            }
            if (!text.isEmpty()) {
                return text.toString();
            }
        }

        JsonNode choices = root.path("choices");
        if (choices.isArray() && !choices.isEmpty()) {
            JsonNode message = choices.get(0).path("message");
            String content = readContentNode(message.path("content"));
            if (!content.isBlank()) {
                return content;
            }
        }

        return "";
    }

    private String readContentNode(JsonNode contentNode) {
        if (contentNode == null || contentNode.isMissingNode() || contentNode.isNull()) {
            return "";
        }
        if (contentNode.isTextual()) {
            return contentNode.asText("");
        }
        if (contentNode.isArray()) {
            StringBuilder text = new StringBuilder();
            for (JsonNode item : contentNode) {
                String value = item.path("text").asText("");
                if (!value.isBlank()) {
                    text.append(value);
                }
            }
            return text.toString();
        }
        return "";
    }
}
