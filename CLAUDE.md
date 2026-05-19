# Ruici-AI-Platform 开发指南（业务导向）

Spring Boot 4.0 + Java 21 + Spring AI 的泛职业文档分析与多场景情景模拟平台。

本文件的目标是让新手在 10 分钟内理解：

- 平台有哪些业务模块、各自分工是什么
- 一条请求从 Controller 到 Service/Repository/基础设施怎么走
- 限流、异步任务（Redis Stream）、LLM Provider 路由等“平台级能力”如何落地

写代码时必须遵守文末的编码规范与禁用清单。

---

## 一、平台业务概览

平台对外提供 5 类业务能力，代码对应 `modules/*`：

- `document`：职业文档上传/解析/去重/异步分析与 PDF 导出（历史类名仍保留 `Resume*`）。
- `simulation`：情景模拟会话（题目生成、答题推进、评估报告、PDF 导出）。
- `knowledgebase`：知识库文件管理、向量化、检索增强问答（含会话式 RAG Chat）。
- `schedule`：邀请文本解析与日程 CRUD（兼容旧路径 `simulation-schedule`）。
- `voice`：WebSocket 实时语音交互（ASR → LLM → TTS）+ 异步评估。

平台级特点（以当前代码实现为准）：

- 统一返回体 `Result<T>`，全局异常通过 `GlobalExceptionHandler` 统一转为 HTTP 200 + 业务错误码。
- 多 LLM Provider：通过 `LlmProviderRegistry` 按 `providerId` 路由并缓存 `ChatClient`。
- 限流：`@RateLimit` + AOP + Redis Lua，支持 `GLOBAL/IP/USER` 多维度叠加。
- 异步任务：Redis Stream 模板化生产/消费，失败重试 3 次，覆盖 4 条管道（文档分析/知识库向量化/情景模拟评估/语音评估）。

---

## 二、项目代码结构

单模块 Maven 项目，按功能分包：

```
com/ruici/ai/
├── RuiciAiApplication.java           # @SpringBootApplication + @EnableScheduling
│
├── common/                           # 通用基础能力
│   ├── annotation/                   #   @RateLimit（可重复注解，滑动窗口限流）
│   ├── aspect/                       #   RateLimitAspect（AOP + Redis Lua 限流）
│   ├── ai/                           #   StructuredOutputInvoker（结构化输出重试）
│   │                                 #   LlmProviderRegistry（多 LLM Provider 注册与缓存）
│   ├── async/                        #   AbstractStreamConsumer/Producer（Redis Stream 模板）
│   ├── config/                       #   配置类（CORS、S3、ObjectMapper、OpenAPI、LlmProvider）
│   ├── constant/                     #   CommonConstants、AsyncTaskStreamConstants
│   ├── evaluation/                   #   评估与报告的通用能力（跨模块复用）
│   ├── exception/                    #   ErrorCode（10 个错误域 1xxx-10xxx）
│   │                                 #   BusinessException、RateLimitExceededException
│   ├── model/                        #   AsyncTaskStatus
│   └── result/                       #   Result<T>（统一响应包装）
│
├── infrastructure/                   # 技术基础设施
│   ├── export/                       #   PdfExportService（iText 8）
│   ├── file/                         #   文件解析（Tika）、存储（S3/RustFS）、校验、清洗
│   ├── mapper/                       #   MapStruct 映射器（Interview、Resume、KB、RagChat）
│   └── redis/                        #   RedisService、InterviewSessionCache
│
└── modules/                          # 业务模块（每个模块自包含 MVC 分层）
    ├── document/                     #   泛职业文档分析：上传、解析、AI 评分、去重
    ├── simulation/                   #   多场景情景模拟：会话、AI 出题、答题评估、报告导出
    ├── knowledgebase/                #   知识库：文档上传、向量化（pgvector）、RAG 查询、聊天会话
    ├── schedule/                     #   日程场景：日历管理、AI 解析邀请文本
    └── voice/                        #   语音交互：WebSocket 实时通话、ASR/TTS、多轮评估
```

**技术栈**：Spring Boot 4.0 / Java 21（虚拟线程）/ Spring AI 2.0 / JPA + PostgreSQL + pgvector / Redisson / Redis Stream / MapStruct / iText 8 / Apache Tika

**前端**：React 18 + TypeScript + Vite + TailwindCSS 4（`frontend/` 目录）

**resources 实际结构补充**（以当前代码为准）：

- `src/main/resources/application.yml`、`application-dev.yml`
- `src/main/resources/logback-spring.xml`
- `src/main/resources/voice-interview-opening.yml`
- `src/main/resources/fonts/`、`prompts/`、`scripts/`、`skills/`

---

## 三、分层架构（新手按这个读代码）

```
Controller → Service → Repository
                ↕
          Infrastructure（RedisService、FileStorageService、PdfExportService）
```

### Controller 层

- 仅路由和委托，禁止业务逻辑
- 路由 + 参数校验 + 限流声明；禁止写业务编排逻辑
- 常见路径：`/api/{module}/...`（少量兼容路径会出现双前缀）
- 使用 `@RateLimit` 注解做限流（`@Repeatable`，每维度独立 count）
- 通过 `@Valid` + `@RequestBody` 校验请求
- Controller 对外语义优先按模块定位理解；若历史类名仍带 `Interview/Resume/VoiceInterview`，要在注释里明确说明兼容背景
- 重构后需把 Controller 接口同步沉淀到 `api/` 对应文档

### Service 层

- 业务编排中心：跨基础设施（存储/Redis/LLM/DB）组合出可用能力
- 模块常见核心服务：
  - `document`：`ResumeUploadService`（上传/去重/入队）、`ResumeGradingService`（分析）、`ResumeHistoryService`（查询/导出）
  - `simulation`：`InterviewSessionService`（会话生命周期）、`InterviewQuestionService`（出题）、`InterviewHistoryService`（导出/详情）
  - `knowledgebase`：`KnowledgeBaseUploadService`（上传/入队）、`KnowledgeBaseQueryService`（RAG 查询/流式）、`RagChatSessionService`（会话式 RAG）
  - `schedule`：`InterviewParseService`（规则优先 + AI 兜底）、`InterviewScheduleService`（CRUD）
  - `voice`：`VoiceInterviewService`（会话/消息/评估触发）、`DashscopeLlmService`（语音场景 LLM）、`QwenAsrService/QwenTtsService`（实时链路）
- LLM 调用统一通过 `LlmProviderRegistry` 获取 `ChatClient`（支持 default/plain/voice 三种 client）。
- 异步任务通过 Redis Stream（`AbstractStreamProducer/AbstractStreamConsumer` 模板）。
- 所有业务异常使用 `BusinessException(ErrorCode.XXX, message)`，禁止 `RuntimeException`

### Repository 层

- Spring Data JPA，继承 `JpaRepository`
- 自定义查询用 `@Query` 或方法命名约定

---

## 四、核心模块如何跑起来（按请求链路理解）

### 4.1 `document` 文档分析

- 入口：`ResumeController`（`/api/documents/*`）。
- 主链路：`ResumeUploadService.uploadAndAnalyze`：校验 → 去重 → 解析文本 → 上传对象存储 → 入库（状态 PENDING）→ 发送 Redis Stream 分析任务。
- 异步消费：`AnalyzeStreamConsumer` 消费任务，调用 `ResumeGradingService` 做分析，落库分析结果并更新状态。

### 4.2 `simulation` 情景模拟

- 入口：`InterviewController`（会话/答题/报告/PDF），`InterviewSkillController`（技能列表/JD 解析）。
- 会话创建：`InterviewSessionService.createSession` 生成题目（`InterviewQuestionService`）+ 持久化（`InterviewPersistenceService`）+ Redis 缓存。
- 答题推进：`submitAnswer` 写入答案，完成后入队评估任务（Redis Stream）。
- 异步评估：`EvaluateStreamConsumer` 生成报告并保存。

### 4.3 `knowledgebase` 知识库 + RAG Chat

- 入口：`KnowledgeBaseController`（上传/向量化/问答/下载），`RagChatController`（会话式流式问答）。
- 上传：`KnowledgeBaseUploadService.uploadKnowledgeBase`：校验/解析 → 存储 → 入库 → 入队向量化任务。
- 向量化：`VectorizeStreamConsumer` 调用 `KnowledgeBaseVectorService.vectorizeAndStore`。
- 问答：`KnowledgeBaseQueryService.queryKnowledgeBase`（同步）/`answerQuestionStream`（SSE 流式）。
- 会话式 RAG：先保存用户消息与 AI 占位，再流式写回完整答案。

### 4.4 `schedule` 日程解析

- 入口：`InterviewScheduleController`（`/api/schedule` 与 `/api/simulation-schedule` 双前缀）。
- 解析：`InterviewParseService.parse`（规则优先，必要时用 LLM 兜底）。
- 定时更新：`ScheduleStatusUpdater` 以 `@Scheduled` 批量更新过期状态。

### 4.5 `voice` 语音交互

- 入口：REST 在 `VoiceInterviewController`；实时链路在 WebSocket `VoiceInterviewWebSocketHandler`（`/ws/voice-interview/{sessionId}`）。
- 实时链路：音频输入 → ASR → LLM 回复 → TTS → WebSocket 下发；消息与状态通过 `VoiceInterviewService` 持久化与缓存。
- 评估：会话结束或手动触发后入队 Redis Stream，`VoiceEvaluateStreamConsumer` 异步生成评估结果。

---

## 五、JavaBean 后缀规则

| 后缀 | 用途 | 示例 |
|------|------|------|
| `XxxEntity` | JPA 持久化 | `ResumeEntity`、`InterviewSessionEntity` |
| `XxxDTO` | 跨层数据传输 | `ResumeListItemDTO`、`SessionResponseDTO` |
| `XxxRequest` | 前端请求体 | `CreateInterviewRequest`、`QueryRequest` |
| `XxxResponse` | 前端响应体 | `QueryResponse`、`SubmitAnswerResponse` |

- 不可变数据载体优先用 `record`（如 `CreateInterviewRequest`、`QueryRequest`）
- Entity 映射用 MapStruct（`@Mapper(componentModel = "spring")`）
- 简单场景可用 `BeanUtils.copyProperties`
- **禁止直接返回 Entity 给前端**

---

## 四、异常与错误码

### ErrorCode 分域规则

| 域 | 范围 | 示例 |
|----|------|------|
| 通用 | 1xxx | BAD_REQUEST(400)、NOT_FOUND(404) |
| 简历 | 2xxx | RESUME_NOT_FOUND(2001) |
| 面试 | 3xxx | INTERVIEW_SESSION_NOT_FOUND(3001) |
| 存储 | 4xxx | STORAGE_UPLOAD_FAILED(4001) |
| 导出 | 5xxx | EXPORT_PDF_FAILED(5001) |
| 知识库 | 6xxx | KNOWLEDGE_BASE_NOT_FOUND(6001) |
| AI 服务 | 7xxx | AI_SERVICE_TIMEOUT(7002) |
| 限流 | 8xxx | RATE_LIMIT_EXCEEDED(8001) |
| 面试日程 | 9xxx | INTERVIEW_SCHEDULE_NOT_FOUND(9001) |
| 语音面试 | 10xxx | VOICE_SESSION_NOT_FOUND(10001) |

### 异常处理规则

- 抛出：`throw new BusinessException(ErrorCode.XXX, "描述信息")`
- **禁止** `throw new RuntimeException(...)` —— 必须用 `BusinessException`
- 全局异常处理器 `GlobalExceptionHandler` 统一返回 HTTP 200 + `Result.error(code, message)`
- `catch (BusinessException e) { throw e; }` 保留业务异常原样抛出

---

## 六、限流组件（@RateLimit）

```java
// 每个 @RateLimit 对应一个维度，各自独立的 count/interval/timeUnit
@RateLimit(dimension = RateLimit.Dimension.GLOBAL, count = 10)
@RateLimit(dimension = RateLimit.Dimension.IP, count = 10)
public Result<QueryResponse> queryKnowledgeBase(...) { ... }
```

- 注解：`@Repeatable`，AOP 切面 `RateLimitAspect` 逐条执行单 key Lua 脚本
- 注解：`@Repeatable` 的方法级注解，支持 `GLOBAL/IP/USER` 多维度叠加；同一方法上多个规则会逐条执行，任一不通过直接拒绝。
- Redis 执行：切面使用 `RedissonClient` 加载并执行 Lua（`scripts/rate_limit_single.lua`），滑动窗口限流。
- Key 设计（实现为准）：
  - `ratelimit:{ClassName:methodName}:global`
  - `ratelimit:{ClassName:methodName}:ip:<clientIp>`
  - `ratelimit:{ClassName:methodName}:user:<userId>`（从 request attribute `userId` 或 header `X-User-Id`）
  - Lua 内部会派生 `:value` 与 `:permits` 两个 key。
- 降级：注解支持 `fallback` 方法名；若未配置或降级执行失败则抛 `RateLimitExceededException`。
- 备注：注解字段包含 `timeout`，但当前切面未实现“等待令牌”语义（只做立即判定）。

---

## 七、异步任务（Redis Stream）

使用 `AbstractStreamProducer` / `AbstractStreamConsumer` 模板：

```java
// 生产者
public class VectorizeStreamProducer extends AbstractStreamProducer<KnowledgeBaseTask> { ... }

// 消费者
public class VectorizeStreamConsumer extends AbstractStreamConsumer<KnowledgeBaseTask> { ... }
```

- 4 条管道：知识库向量化、职业文档分析、情景模拟评估、语音评估。
- 常量统一定义在 `AsyncTaskStreamConstants`（包含 key/group/字段名/批次/重试次数等）。
- 模板：生产者 `AbstractStreamProducer` 负责入队与失败回写；消费者 `AbstractStreamConsumer` 负责消费循环、ACK、失败重试。
  - 子类只需要实现 `processBusiness()`（不是 `processMessage()`）。
- 重试：最多 3 次，超过后标记 FAILED；每次失败会重新入队并携带 `retryCount`。
- **失败重试**：最大 3 次，超过后标记 FAILED
- **实体删除**：异步处理前校验实体是否存在，不存在直接 ACK 丢弃

---

## 八、AI 服务调用（Provider 路由 + 结构化输出）

### LLM Provider

### 8.1 Provider 路由（LlmProviderRegistry）

- Provider 配置来自 `app.ai.default-provider` 与 `app.ai.providers.*`。
- 获取 client：
  - `getChatClientOrDefault(providerId)`：`providerId` 为空/空白时回落到默认 provider。
  - `getChatClient(providerId)`：providerId 不存在会抛 `IllegalArgumentException`。
- client 形态：
  - `default`：默认带 SkillsTool（若存在）+ Advisors（可配置开关）。
  - `plain`：不带工具调用（用于不需要 tool call 的出题/解析等场景）。
  - `voice`：语音专用 client（SkillsTool + 流式 ToolCallAdvisor；不启用 memory advisor）。

### 8.2 结构化输出（StructuredOutputInvoker）

- 统一入口：`structuredOutputInvoker.invoke(...)`，对 `BeanOutputConverter` 解析失败做最多 N 次重试。
- 重试策略可配置：是否注入“上次失败原因”、是否追加严格 JSON 指令、错误信息截断长度、是否打点指标。

### 8.3 模块级 Provider 覆盖

- `knowledgebase`：`app.ai.rag.llm-provider`（RAG 问答可单独指定 provider）。
- `voice`：`app.voice-interview.llm-provider`（语音模块默认 provider，历史前缀保留为兼容）。

- 配置：`app.ai.providers.{providerId}.baseUrl/apiKey/model`
- 默认聊天 Provider：`app.ai.default-provider`，应优先配置为第三方 OpenAI-compatible 中转；Qwen 主要保留给向量化与语音

### 结构化输出

```java
// 使用 StructuredOutputInvoker 做重试包装
var result = structuredOutputInvoker.invoke(
  chatClient,
  systemPrompt,
  userPrompt,
  outputConverter,
  ErrorCode.AI_SERVICE_ERROR,
  "结构化输出失败：",
  "KnowledgeBaseQuery",
  log
);
```

### Prompt 模板

- 存放在 `resources/prompts/`，使用 StringTemplate（`.st`）格式
- 语音模块兼容配置还可能保留 `voice-interview` 命名，但业务语义按通用语音交互理解
- 语音开场白配置文件：`resources/voice-interview-opening.yml`

---

## 八、格式与命名

- **2 空格缩进**，列限制 100 字符
- 类名 UpperCamelCase，方法名 lowerCamelCase，常量 UPPER_SNAKE_CASE
- **禁止通配符导入**
- 优先 `record` 作为不可变数据载体
- 使用现代 Java 特性：`switch` 表达式、pattern matching `instanceof`、text blocks
- 避免内联全限定类名（用 import 代替）

---

## 九、事务规则

- `@Transactional` 放 Service 层
- **禁止**在事务方法内调用外部 API（LLM 调用、S3 上传等）
- **禁止**同类内部调用 `@Transactional` 方法（AOP 代理不生效）
- 保持事务范围最小

---

## 十、日志规范

- 使用 SLF4J（`@Slf4j`）
- 结构化日志：`log.info("Session created: sessionId={}, role={}", id, role)`
- 异常作为最后一个参数：`log.error("Evaluation failed: sessionId={}", id, e)`
- **禁止** `log.error("Error: {}", e.getMessage())`（丢失堆栈）

---

## 十一、数据库

- PostgreSQL + pgvector（向量搜索，1024 维 COSINE）
- JPA 实体使用 `@Data`、`@Builder`、`@NoArgsConstructor`、`@AllArgsConstructor`
- `ddl-auto` 开发环境 `update`，生产环境 `false`（表结构由 JPA Entity 注解驱动，无需手动迁移）

---

## 十二、配置管理

- 配置文件：`application.yml` + `.env`（通过 `spring.config.import`）
- 敏感信息（API Key、数据库密码）放 `.env`，不入版本控制
- 业务配置用 `@ConfigurationProperties`（如 `VoiceInterviewProperties`、`AppConfigProperties`）
- **禁止** `@Value` 散落在 Service 中（集中到 Properties 类）

### 安全数据区（基于 `.gitignore`）

- 环境变量与密钥：`.env`、`.env.*`（仅提交 `.env.example` 模板）。
- 本地 Spring 覆盖配置：`application-local.*`、`application-dev-local.*`、`bootstrap-local.*`。
- 证书/私钥/Keystore：`*.pem`、`*.key`、`*.p12`、`*.pfx`、`*.jks`、`id_rsa` 等。
- 内部资料目录：`private/`（禁止放生产密钥、真实用户数据、可复用 token）。
- 工具缓存目录：`.playwright-mcp/`、`.playwright-cli/`（可能携带会话态）。

另外：`.gitignore` 默认忽略 `src/main/resources/application-dev.yml`。

- 团队协作建议：提交不含密钥的 `application-dev.example.yml`，本地使用 `application-dev.yml` 覆盖。
- 无论哪种方式，禁止在可入库文件中写入真实密钥。

---

## 十三、测试

- JUnit 5 + Mockito + AssertJ
- `@DisplayName` 中文描述测试意图
- `@Nested` 按功能分组测试
- 集成测试用 H2 内存数据库（`application-test.yml`）
- 限流测试需要真实 Redis

---

## 速查：禁止清单

| 禁止项 | 原因 |
|--------|------|
| `throw new RuntimeException(...)` | 绕过全局异常处理，用 `BusinessException` |
| 直接返回 Entity 给前端 | 暴露内部结构 |
| `@Value` 散落在 Service 中 | 配置应集中到 `@ConfigurationProperties` |
| 内联全限定类名（`org.springframework...`） | 用 import 代替 |
| 事务内调用外部 API（LLM、S3） | 占用 DB 连接 |
| 同类内部调用 `@Transactional` | AOP 代理不生效 |
| `catch (Exception e) {}` 静默忽略 | 隐藏错误 |
| 循环调用 DB | 改用批量操作 |
| 硬编码密钥 | 安全风险 |
| `Executors.newXxxThreadPool()` | OOM 风险，用 `ThreadPoolExecutor` |
