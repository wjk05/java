<div align="center">

**Ruici-AI-Platform**

泛职业文档分析 + 多场景情景模拟平台，基于大语言模型、知识库与实时语音能力构建。

[![Java](https://img.shields.io/badge/Java-21-orange?logo=openjdk)](https://openjdk.org/)
[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-4.0-green?logo=springboot)](https://spring.io/projects/spring-boot)
[![Spring AI](https://img.shields.io/badge/Spring%20AI-2.0.0--M4-6DB33F)](https://spring.io/projects/spring-ai)
[![React](https://img.shields.io/badge/React-18.3-blue?logo=react)](https://react.dev/)
[![PostgreSQL](https://img.shields.io/badge/PostgreSQL-pgvector-336791?logo=postgresql)](https://www.postgresql.org/)

</div>

---

## 项目定位

`Ruici-AI-Platform` 是一个面向多职业者的真实业务场景的 AI 平台。

项目当前的核心方向是：

- **泛职业文档分析**：进行各种职业者的文档理解、结构提取、评估建议、报告导出。
- **多情景情景模拟**：进行情景模拟, AI 充当模拟对象, 根据文档信息与方向对职业者进行拷打。
- **知识库驱动能力编排**：通过知识库、提示词模板和 Skill 组合出不同垂类能力。

默认三个情景模拟方向是：

1. **求职面试**：面试官角度，围绕相关岗位的准备、模拟问答、文档评估、语音面试展开。
2. **专业答疑**：提问者角度，围绕相关专业的文档分析、专业问答和场景化解释展开。
3. **职业沟通表达**：职场同事/高层角度，围绕汇报、邮件、会议发言、跨部门协作与反馈沟通，进行话术优化、场景演练和表达评估。

## AI Provider 策略

项目采用 **多 Provider 架构**，但默认策略已经切换为“自定义 OpenAI-compatible 中转优先”。

### 默认路由

- **主聊天 / 通用推理 / 情景模拟**：默认走第三方 OpenAI-compatible 中转
  - Base URL: `https://api-s.zwenooo.link/v1`
  - Model: `gpt-5.2`
- **向量化 / Embedding**：默认保留 `Qwen / DashScope`
- **语音 ASR / TTS / Realtime**：继续保留 `Qwen / DashScope`

### 为什么这样设计

- 第三方 OpenAI-compatible 中转更适合作为统一聊天入口，方便切换模型和成本控制。
- 向量化与实时语音的兼容性、稳定性和能力覆盖目前仍以 `Qwen / DashScope` 更稳妥。
- “OpenAI-compatible” 不是“完全等价 OpenAI”，不同中转在 `/chat/completions`、流式事件、工具调用、结构化输出上可能存在差异，因此需要把聊天和语音/向量能力分层配置，而不是强行共用一个 Provider。

## 技术栈

### 后端

| 技术 | 版本 | 说明 |
| --- | --- | --- |
| Spring Boot | 4.0.1 | 应用框架 |
| Java | 21 | 开发语言，启用虚拟线程 |
| Spring AI | 2.0.0-M4 | OpenAI-compatible 集成与向量能力 |
| PostgreSQL + pgvector | 16+ | 关系数据库与向量检索 |
| Redis + Redisson | 7+ / 4.0.0 | 缓存、限流、Redis Stream |
| Apache Tika | 2.9.2 | 文档解析 |
| iText 8 | 8.0.5 | PDF 导出 |
| MapStruct | 1.6.3 | 对象映射 |
| DashScope SDK | 2.22.7 | Qwen 实时语音 ASR/TTS |
| Maven | 3.8.8+ | 构建工具 |

### 前端

React 18 + TypeScript + Vite + TailwindCSS 4，位于 `frontend/` 目录。

## 当前能力地图

### 1. 文档分析

- 支持 PDF、DOCX、DOC、TXT、Markdown 等多格式输入。
- 支持内容清洗、结构抽取、评估建议、异步处理和 PDF 报告导出。
- 当前代码中仍有部分 `resume` 命名残留，但业务语义已经按“通用职业文档”理解。

### 2. 情景模拟

- 支持基于 Skill 的题目/话术生成、多轮追问、评估与报告导出。
- 当前默认能力仍以求职面试为主，但平台目标是承载更多场景模板。
- 三个默认专项可通过 `知识库 + prompts + skills` 继续扩展。

### 3. 知识库问答

- 支持文档上传、切分、向量化、检索增强生成（RAG）和流式回答。
- 适合作为中医药答疑、小说知识库、岗位知识库等垂类能力底座。

### 4. 语音交互

- 语音能力继续使用 `Qwen / DashScope` 实时链路。
- 适合面试模拟、口语问答、角色扮演等低延迟场景。

## 业务结构说明

从产品语义上，`Ruici` 的业务结构应该理解为：

| 平台语义 | 当前代码承载 |
| --- | --- |
| `document` 文档分析 | `modules/document` |
| `simulation` 情景模拟 | `modules/simulation` |
| `knowledgebase` 知识库 | `modules/knowledgebase` |
| `schedule` 日程场景 | `modules/schedule` |
| `voice` 语音交互 | `modules/voice` |

部分配置键、数据库字段和兼容命名仍然带有旧项目色彩，这是当前重构过程中的兼容层，不代表最终产品语义。

前后端对接时，建议直接查看 `api/` 目录下的模块接口说明，而不是只根据类名猜测语义。

## 项目结构

```text
ruici-ai-platform/
├── src/main/java/com/ruici/ai/
│   ├── common/                # 通用能力：异常、限流、Provider 路由、Redis Stream
│   ├── infrastructure/        # 文件、导出、Redis、映射等基础设施
│   └── modules/
│       ├── document/          # 文档上传、解析、分析、导出
│       ├── simulation/        # 情景模拟、题目生成、回答评估
│       ├── knowledgebase/     # 知识库上传、向量化、RAG 问答
│       ├── schedule/          # 场景化日程解析与管理
│       └── voice/             # 语音面试 / 语音交互 / ASR / TTS
├── src/main/resources/
│   ├── application.yml
│   ├── application-dev.yml
│   ├── logback-spring.xml
│   ├── voice-interview-opening.yml
│   ├── fonts/
│   ├── prompts/
│   ├── scripts/
│   └── skills/
├── frontend/                  # 前端
├── docker/                    # 脚本
├── word                       # 测试报告
├── api/                       # 接口文档
├── .env.example
└── docker-compose.yml
```

## 快速开始

### 1. 环境要求

| 依赖 | 版本 | 必需 | 说明 |
| --- | --- | --- | --- |
| JDK | 21+ | 是 | 后端运行环境 |
| Maven | 3.8.8+ | 是 | 后端构建工具 |
| Node.js | 18+ | 是 | 前端开发环境 |
| Docker | 最新版 | 推荐 | 本地启动 PostgreSQL / Redis / MinIO |

在真正启动前，先记住 4 个最容易混淆的初始化职责：

- **API Key**：由你自己在 `.env` 或环境变量里配置。
- **数据库 `ruici_ai_platform`**：`docker compose` 会自动创建；手动安装 PostgreSQL 时需要自己创建。
- **数据表**：由 `spring.jpa.hibernate.ddl-auto=update` 在开发环境自动补齐。
- **对象存储 bucket**：完整 `docker-compose.yml` 会自动创建；`docker-compose.dev.yml` 需要你手动创建 `ruici-ai-platform`。

### 2. 配置环境变量

推荐先复制模板：

```bash
cp .env.example .env
```

至少需要按场景配置两类 Provider：

- **第三方 OpenAI-compatible 中转**：用于默认聊天与情景模拟
- **DashScope / Qwen**：用于向量化和语音

核心变量如下：

```bash
AI_DEFAULT_PROVIDER=third-party
THIRD_PARTY_BASE_URL=https://api-s.zwenooo.link/v1
THIRD_PARTY_API_KEY=your_gateway_key
THIRD_PARTY_MODEL=gpt-5.2
THIRD_PARTY_FALLBACK_MODEL=qwen-plus

AI_BAILIAN_API_KEY=your_dashscope_key
AI_EMBEDDING_MODEL=text-embedding-v3
APP_VOICE_INTERVIEW_LLM_PROVIDER=dashscope
```

完整字段说明见 `SETUP_API_KEYS.md` 与 `.env.example`。

> 安全提醒：请仅在 `.env` 或环境变量中填写真实密钥，严禁把真实 `API Key/Token/Password` 写入
> `README`、`SETUP_API_KEYS.md`、测试用例或其他会进入版本库的文件。

#### 安全数据区（基于 `.gitignore`）

为了降低误提交敏感信息的风险，本项目把以下内容视为“安全数据区”，默认不进入版本控制：

- 环境变量与密钥：`.env`、`.env.*`（仅保留 `.env.example` 作为模板）。
- 本地 Spring 覆盖配置：`application-local.*`、`application-dev-local.*`、`bootstrap-local.*`。
- 证书/私钥/Keystore：`*.pem`、`*.key`、`*.p12`、`*.pfx`、`*.jks`、`id_rsa` 等。
- 内部资料与提示词草案：`private/`。
- 工具缓存（可能携带 token/会话态）：`.playwright-mcp/`、`.playwright-cli/`。

另外，`.gitignore` 里也默认忽略 `src/main/resources/application-dev.yml`：

- 如果你需要团队共享开发环境默认配置，建议改为提交一个不含密钥的 `application-dev.example.yml`，并在本地用 `application-dev.yml` 覆盖。
- 如果你只在本地开发使用它，请确保里面不出现真实 `API Key/Password`。

#### 2.1 关于“旧键名”和“新语义”的关系

为了兼容当前代码，项目里还有一些历史命名没有一次性全部改掉。新手第一次看到时，最容易误会它们只服务于面试场景。

你可以先按下面这张表理解：

| 现有键名 / 目录名 | 现在真正的语义 |
| --- | --- |
| `app.resume` / `resume` | 通用职业文档分析 |
| `app.interview` / `interview` | 情景模拟主流程 |
| `app.voice-interview` | 语音交互 / 语音情景模拟 |
| `APP_VOICE_INTERVIEW_LLM_PROVIDER` | 语音模块默认 Provider |

也就是说：**看到旧命名时，优先按 Ruici 的新业务语义理解，不要按旧项目 InterviewGuide 的边界去理解。**

### 3. 启动基础设施

推荐新手先用开发编排启动基础设施：

```bash
docker compose -f docker-compose.dev.yml up -d
```

启动完成后，请按下面顺序检查：

#### 3.1 PostgreSQL 是否已自动建库

`docker-compose.dev.yml` 里已经设置了 `POSTGRES_DB=ruici_ai_platform`，因此 **容器首次启动时会自动创建数据库**。

如果你不是用 Docker，而是本地自己装的 PostgreSQL，请手动执行：

```sql
CREATE DATABASE ruici_ai_platform;
\c ruici_ai_platform
CREATE EXTENSION IF NOT EXISTS vector;
```

这里要特别注意：

- `ddl-auto` **只能建表，不能建数据库**。
- `docker/postgres/init.sql` 只负责执行 `CREATE EXTENSION IF NOT EXISTS vector;`，不负责建库。

#### 3.2 对象存储 bucket 是否已创建

如果你使用的是 `docker-compose.dev.yml`：

- 访问 `http://localhost:9001`
- 登录对象存储控制台
- **手动创建** bucket：`ruici-ai-platform`

如果你使用的是根目录完整编排 `docker-compose.yml`，项目里的 `createbuckets` 初始化容器会自动创建这个 bucket。

#### 3.3 `ddl-auto` 在开发环境应该是什么

开发环境推荐保持：

```yaml
spring:
  jpa:
    hibernate:
      ddl-auto: update
```

原因是：

- `update`：适合开发，缺表时自动创建，已有数据尽量保留。
- `create`：每次启动都可能删表重建，新手很容易误删数据。
- `validate`：适合生产，只校验，不自动建表。

### 4. 启动后端

```bash
mvn spring-boot:run
```

如果启动失败，请优先检查下面 5 项：

1. 当前 Java 版本是否是 **21**。
2. `.env` 中的 `THIRD_PARTY_API_KEY` 和 `AI_BAILIAN_API_KEY` 是否已配置。
3. PostgreSQL 中是否存在数据库 `ruici_ai_platform`。
4. PostgreSQL 中是否已启用 `vector` 扩展。
5. 对象存储中是否已存在 bucket `ruici-ai-platform`。

后端默认地址：`http://localhost:8080`

### 5. 启动前端

```bash
cd frontend
pnpm install
pnpm dev
```

前端默认地址：`http://localhost:5173`

## Docker 部署

项目根目录提供 `docker-compose.yml`，可启动：

- PostgreSQL + pgvector
- Redis
- MinIO
- Spring Boot 后端
- React 前端

启动方式：

```bash
docker compose up -d --build
```

## 常见问题

### 向量化为什么仍然依赖 Qwen / DashScope？

因为当前项目保留了 `Qwen / DashScope` 作为稳定的 Embedding 与实时语音能力来源，这部分不跟随主聊天 Provider 一起切换。

### 为什么默认不是直接用 OpenAI？

因为项目当前的主策略是 **自定义 OpenAI-compatible 中转优先**，OpenAI 原生 Provider 作为可选项保留在多 Provider 注册表中。

### OpenAI-compatible 一定完全兼容吗？

不一定。不同网关在 `chat/completions`、流式事件结构、工具调用和结构化输出上可能存在差异。项目已经采用 Provider 分层：聊天默认走中转，语音/向量保留 Qwen。

## 内部说明

`private/项目重构提示词-优化.md` 是当前重构的设计基线之一。对外文档以本 `README` 为准，对内设计可继续沿 `private/` 目录逐步推进。

同时，`api/README.md` 汇总了当前后端接口入口，供前端逐步迁移使用。
