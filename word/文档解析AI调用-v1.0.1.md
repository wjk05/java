# 文档解析 AI修复总结 v1.0.1

## 1. 背景

本次问题出现在“泛职业文档分析”链路中：用户上传 PDF 后，文件解析、对象存储、入库和
Redis Stream 投递均成功，但异步分析任务调用第三方 OpenAI-compatible 中转时失败。

关键结论：文档分析链路使用 `ResumeGradingService -> LlmProviderRegistry -> Spring AI
OpenAiApi`，它与知识库 RAG 中使用的 `OpenAiCompatibleGatewayClient` 不是同一条调用路径。
因此，虽然 RAG 链路已有中转端点兼容逻辑，文档分析仍会受到 Spring AI 默认路径拼接规则影响。

## 2. 原始现象

上传日志显示 PDF 文本解析成功：

```text
文件解析成功，提取文本长度: 3419 字符
职业文档上传处理完成: 简历.pdf, documentId=1
开始处理职业文档分析任务: documentId=1
开始分析简历，文本长度: 3419 字符
```

随后 AI 结构化输出调用失败：

```text
404 - {"error":{"message":"Invalid URL (POST /v1/v1/chat/completions)",
"type":"invalid_request_error","param":"","code":""}}
```

这说明中转收到的实际请求路径是：

```text
/v1/v1/chat/completions
```

而正确路径应是：

```text
/v1/chat/completions
```

## 3. 根因

项目 README、`.env.example` 和 `application.yml` 均建议第三方中转配置为：

```text
THIRD_PARTY_BASE_URL=https://api-s.zwenooo.link/v1
```

这个配置对项目自研的 `OpenAiCompatibleGatewayClient` 是合理的，因为该客户端会显式构造候选端点，
已含 `/v1` 时不会重复追加。

但文档分析链路走的是 Spring AI `OpenAiApi`。Spring AI 2.0.0-M4 的 `OpenAiApi` 默认
`completionsPath` 是：

```text
/v1/chat/completions
```

因此当 `LlmProviderRegistry` 直接把 `https://api-s.zwenooo.link/v1` 传给
`OpenAiApi.builder().baseUrl(...)` 时，最终请求会变成：

```text
https://api-s.zwenooo.link/v1/v1/chat/completions
```

## 4. 修复目标

本次修复目标是：

1. 保持现有配置兼容，允许 `THIRD_PARTY_BASE_URL` 继续写成带 `/v1` 的形式。
2. 修复 `ResumeGradingService` 文档分析调用 Spring AI 时的重复路径问题。
3. 不扩大影响面，不重构文档分析业务流程。
4. 增加单元测试锁定 URL 规范化行为。

## 5. 已落地改动

### 5.1 `LlmProviderRegistry` 增加 OpenAI API base-url 规范化

文件：

```text
src/main/java/com/ruici/ai/common/ai/LlmProviderRegistry.java
```

核心调整：

```java
String normalizedBaseUrl = normalizeBaseUrlForOpenAiApi(config.getBaseUrl());

OpenAiApi openAiApi = OpenAiApi.builder()
    .baseUrl(normalizedBaseUrl)
    .apiKey(config.getApiKey())
    .restClientBuilder(restClientBuilder)
    .build();
```

新增规范化规则：

1. `baseUrl` 为空时直接抛出配置异常。
2. 去掉末尾多余 `/`。
3. 如果末尾是 `/v1`，传给 Spring AI 前移除该版本段。
4. 如果不是 `/v1` 结尾，则保持原路径。

示例：

| 原始配置 | 传给 Spring AI 的 base-url | Spring AI 最终路径 |
| --- | --- | --- |
| `https://api-s.zwenooo.link/v1` | `https://api-s.zwenooo.link` | `/v1/chat/completions` |
| `https://dashscope.aliyuncs.com/compatible-mode/v1/` | `https://dashscope.aliyuncs.com/compatible-mode` | `/compatible-mode/v1/chat/completions` |
| `http://localhost:1234` | `http://localhost:1234` | `/v1/chat/completions` |

### 5.2 修复模型别名候选中的双横线问题

文件：

```text
src/main/java/com/ruici/ai/common/ai/OpenAiCompatibleGatewayClient.java
```

测试过程中发现既有逻辑会把 `gpt-5.2` 额外生成非法候选：

```text
gpt--5.2
```

已调整为：只有模型名不是 `gpt-` 开头时，才尝试把 `gpt` 转成 `gpt-` 形式。
修复后 `gpt-5.2` 的候选为：

```text
gpt-5.2 -> gpt5.2
```

## 6. 单元测试覆盖

### 6.1 新增测试

文件：

```text
src/test/java/com/ruici/ai/common/ai/LlmProviderRegistryTest.java
```

覆盖点：

1. `baseUrl` 已含 `/v1` 时移除，避免 `/v1/v1`。
2. `baseUrl` 含业务路径和 `/v1/` 时，只移除末尾版本段，保留业务路径。
3. `baseUrl` 无 `/v1` 时保持原路径。
4. `baseUrl` 为空时抛出配置异常。

### 6.2 更新测试

文件：

```text
src/test/java/com/ruici/ai/common/ai/OpenAiCompatibleGatewayClientTest.java
```

更新模型候选断言，确认 `gpt-5.2` 不再生成 `gpt--5.2`。

## 7. 修复后的情况

修复后，文档分析链路继续按原流程运行：

```text
PDF 上传 -> 文本解析 -> RustFS 存储 -> 入库 -> Redis Stream -> ResumeGradingService AI 分析
```

区别在于 `ResumeGradingService` 获取的默认 `ChatClient` 在构建 `OpenAiApi` 时，会先规范化
Provider base URL。对于当前推荐配置：

```text
THIRD_PARTY_BASE_URL=https://api-s.zwenooo.link/v1
```

运行时会传给 Spring AI：

```text
https://api-s.zwenooo.link
```

Spring AI 再追加自己的默认 completions path：

```text
/v1/chat/completions
```

最终请求恢复为正确路径：

```text
https://api-s.zwenooo.link/v1/chat/completions
```

## 8. 验证结果

本地默认 `java -version` 是 JDK 8，Maven 默认使用 JDK 20，直接运行 Maven 会因为项目要求
Java 21 而失败：

```text
错误: 不支持发行版本 21
```

已显式使用本机 JDK 21 执行验证：

```powershell
$env:JAVA_HOME='D:\project\javazhong\Jdk21'
$env:Path="$env:JAVA_HOME\bin;$env:Path"
mvn "-Dtest=LlmProviderRegistryTest,OpenAiCompatibleGatewayClientTest" test
mvn test
```

验证结果：

```text
Focused tests: Tests run: 7, Failures: 0, Errors: 0, Skipped: 0
Full tests:    Tests run: 14, Failures: 0, Errors: 0, Skipped: 0
BUILD SUCCESS
```

## 9. 后续注意事项

1. 启动后端时必须确保使用 JDK 21，否则 Maven 编译会失败。
2. `THIRD_PARTY_BASE_URL` 仍可继续按 README 写成带 `/v1` 的形式。
3. 如果以后改用 Spring AI 的可配置 `completionsPath`，需要重新评估本次 base-url 规范化规则，避免重复修正。
4. 文档分析链路和 RAG 链路目前仍是两套 AI 调用路径：前者走 Spring AI `ChatClient`，后者对
   `third-party` 走自研 `OpenAiCompatibleGatewayClient`。
