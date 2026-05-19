# 中转 AI 适配修复总结 v1.0.1

## 1. 背景

本次问题出现在知识库 RAG 聊天流式链路中。项目默认聊天 Provider 使用第三方
OpenAI-compatible 中转，但运行时连续暴露出以下问题：

1. 请求路径可能打到中转根路径，而不是 `/v1` 路径。
2. Spring AI 默认 OpenAI ChatClient 对第三方中转的非标准响应兼容不足。
3. 第三方中转返回 `503`，但语义错误码是 `model_not_found`，说明当前模型在该中转账号下无可用渠道。

关键结论：OpenAI-compatible 不能简单理解为“只改 base-url 即可”。不同中转在端点、
SSE 事件、完成态 JSON、模型命名和计费渠道上都可能与官方 OpenAI 不完全一致。

## 2. 原始现象

### 2.1 路径错误

早期日志中请求打到了：

```text
https://api-s.zwenooo.link/responses
https://api-s.zwenooo.link/chat/completions
```

而正确路径应优先是：

```text
https://api-s.zwenooo.link/v1/responses
https://api-s.zwenooo.link/v1/chat/completions
```

原因是本地 `.env` 中 `THIRD_PARTY_BASE_URL` 曾配置为根路径，少了 `/v1`。

### 2.2 响应兼容不足

Spring AI 2.0.0-M4 的 OpenAI 客户端主要围绕 Chat Completions 语义设计，公开调用点是：

- `chatCompletionEntity`
- `chatCompletionStream`

它没有直接提供 `/responses` 客户端，也不会自动理解所有第三方中转的 SSE 或完成态
JSON 结构。

### 2.3 模型不可用

路径修正后，中转返回：

```json
{
  "error": {
    "type": "transfer_api_error",
    "code": "model_not_found",
    "message": "模型无可用渠道（billing）"
  }
}
```

虽然 HTTP 状态是 `503`，但语义上不是普通临时故障，而是当前模型在该中转分组下不可用。
继续重试同一个模型没有意义，应快速切换候选模型。

## 3. 修复目标

修复目标不是绕过中转，而是在保留第三方中转作为主聊天入口的前提下，让 RAG 链路具备：

1. 正确路径选择能力。
2. `/responses` 与 `/chat/completions` 双端点兼容能力。
3. 多种 SSE / JSON 响应提取能力。
4. 模型候选回退能力。
5. 对非第三方 Provider 保持原有 Spring AI 行为，不扩大影响面。

## 4. 已落地改动

### 4.1 新增中转兼容客户端

文件：

```text
src/main/java/com/ruici/ai/common/ai/OpenAiCompatibleGatewayClient.java
```

职责：

- 仅对 `third-party` Provider 启用。
- 优先尝试 `/responses`。
- `/responses` 失败后回退到 `/chat/completions`。
- 同时支持非流式改写和流式 RAG 回答。
- 显式构造完整候选端点，避免路径拼接歧义。
- 支持模型候选链，避免单个模型不可用时整条链路失败。

### 4.2 新增响应提取器

文件：

```text
src/main/java/com/ruici/ai/common/ai/OpenAiCompatibleResponseExtractor.java
```

兼容的文本来源包括：

- `response.output_text.delta`
- `response.output_text.done`
- `response.content_part.done`
- `output_text`
- `response.output_text`
- `output[].content[].text`
- `response.output[].content[].text`
- `choices[].message.content`
- `choices[].delta.content`

这样可以覆盖官方 OpenAI Chat Completions、Responses API，以及部分第三方中转的变体返回。

### 4.3 RAG 链路接入兼容客户端

文件：

```text
src/main/java/com/ruici/ai/modules/knowledgebase/service/KnowledgeBaseQueryService.java
```

调整后：

- `third-party` 走 `OpenAiCompatibleGatewayClient`。
- 非 `third-party` 继续走原 Spring AI `ChatClient`。
- Query rewrite 失败时继续保留“使用原问题检索”的降级行为。
- 最终 RAG 流式回答也走同一套中转兼容逻辑。

### 4.4 修正配置与增加备用模型

相关文件：

```text
src/main/resources/application.yml
.env.example
README.md
SETUP_API_KEYS.md
```

关键配置：

```yaml
app:
  ai:
    providers:
      third-party:
        base-url: ${THIRD_PARTY_BASE_URL:https://api-s.zwenooo.link/v1}
        model: ${THIRD_PARTY_MODEL:gpt5.2}
        fallback-model: ${THIRD_PARTY_FALLBACK_MODEL:${AI_CHAT_FALLBACK_MODEL:qwen-plus}}
```

当前模型候选链示例：

```text
gpt5.2 -> gpt-5.2 -> qwen-plus
```

其中：

- `gpt5.2` 来自主模型配置。
- `gpt-5.2` 是对可能存在的模型命名差异做兼容。
- `qwen-plus` 是备用模型。

### 4.5 配置实体扩展

文件：

```text
src/main/java/com/ruici/ai/common/config/LlmProviderProperties.java
```

新增字段：

```java
private String fallbackModel;
```

用于在同一 Provider 下配置备用模型。

## 5. 修复后的行为

### 5.1 third-party Provider

当 `app.ai.rag.llm-provider=third-party` 时：

1. 构造端点候选。
2. 构造模型候选。
3. 优先尝试 `/responses`。
4. 如果 `/responses` 不可用，回退到 `/chat/completions`。
5. 如果当前模型返回 `model_not_found` 或其他失败，尝试下一个模型候选。
6. 从多种 SSE / JSON 结构中提取文本。

也就是说，`third-party` 现在是“增强兼容模式”。

### 5.2 标准 OpenAI 格式

如果中转或官方 OpenAI 服务支持标准 `/chat/completions`，当前实现也能继续使用标准格式。

第三方 Provider 的兼容客户端本质上是：

```text
Responses API 优先 -> Chat Completions 兜底 -> 多响应格式提取 -> 多模型候选回退
```

因此它既能适配中转，也保留了标准 OpenAI Chat Completions 格式能力。

### 5.3 其他 Provider

`dashscope`、`openai`、`lmstudio` 等非 `third-party` Provider 不走该兼容客户端，继续使用原有
Spring AI `ChatClient` 路径，避免影响语音、向量化和其他模块。

## 6. 单元测试覆盖

新增 / 更新测试：

```text
src/test/java/com/ruici/ai/common/ai/OpenAiCompatibleGatewayClientTest.java
src/test/java/com/ruici/ai/common/ai/OpenAiCompatibleResponseExtractorTest.java
```

覆盖点：

1. baseUrl 无路径时优先补 `/v1`。
2. baseUrl 已含 `/v1` 时保持原路径。
3. 模型候选包含主模型、命名变体和备用模型。
4. 提取 Responses API SSE `response.output_text.delta`。
5. 提取 Responses 完成态 `response.output[].content[].text`。
6. 提取 Chat Completions 非流式 `choices[].message.content`。
7. 提取 Chat Completions 流式 `choices[].delta.content`。

已执行验证：

```bash
mvn "-Dtest=OpenAiCompatibleGatewayClientTest,OpenAiCompatibleResponseExtractorTest" test
mvn test
```

结果：全部通过。

## 7. 后续排障判断标准

如果后续仍然失败，按以下顺序判断：

1. 日志中的请求路径是否包含 `/v1`。
2. 日志中是否已经尝试了多个模型候选。
3. 如果所有模型都返回 `model_not_found`，说明不是代码问题，而是该中转账号没有这些模型渠道。
4. 此时应修改配置，而不是继续改协议解析。

推荐调整：

```bash
THIRD_PARTY_MODEL=该中转实际支持的主模型
THIRD_PARTY_FALLBACK_MODEL=该中转实际支持的备用模型
```

不要把真实 API Key 写入文档或提交到仓库。

## 8. 一句话总结

本次修复不是简单修改配置，而是给 RAG 的第三方 OpenAI-compatible 中转补了一层
“端点兼容 + 响应提取 + 模型候选回退”的适配层。修复后，RAG 既可以继续使用第三方中转，
也能兼容标准 OpenAI Chat Completions 格式，并且不会影响非第三方 Provider 的原有行为。
