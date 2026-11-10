<div align="center">

**Ruici-AI-Platform**

泛职业文档分析 + 多场景情景模拟平台，基于大语言模型、知识库与实时语音能力构建。

[![Java](https://img.shields.io/badge/Java-21-orange?logo=openjdk)](https://openjdk.org/)
[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-4.0.1-green?logo=springboot)](https://spring.io/projects/spring-boot)
[![Spring AI](https://img.shields.io/badge/Spring%20AI-2.0.0--M4-6DB33F)](https://spring.io/projects/spring-ai)
[![React](https://img.shields.io/badge/React-18.3-blue?logo=react)](https://react.dev/)
[![PostgreSQL](https://img.shields.io/badge/PostgreSQL-pgvector-336791?logo=postgresql)](https://www.postgresql.org/)

</div>

---

## 项目定位

`Ruici-AI-Platform` 是一个面向多职业真实业务场景的 AI 平台，当前聚焦三件事：

- **泛职业文档分析**：文档理解、结构提取、评估建议、异步分析与 PDF 报告导出。
- **多场景情景模拟**：AI 扮演模拟对象，基于文档与场景方向进行多轮问答、追问与评估。
- **知识库驱动能力编排**：通过知识库、提示词模板与 Skill 组合出不同垂类能力（RAG 问答、专业答疑等）。

默认的三个情景模拟方向：

1. **求职面试**：面试官视角，覆盖文档评估、模拟问答、追问与语音面试。
2. **专业答疑**：提问者视角，围绕专业知识进行文档分析、问答与场景化解释。
3. **职业沟通表达**：同事 / 上级视角，围绕汇报、邮件、会议发言与跨部门协作进行话术优化和演练。

## 功能一览

| 模块 | 能力 | 主要入口 |
| --- | --- | --- |
| `document` 文档分析 | PDF / DOCX / DOC / TXT / Markdown 上传、解析、去重、AI 评分、历史查询、PDF 导出 | `/api/documents` |
| `simulation` 情景模拟 | 会话创建、AI 出题、答题推进、追问、异步评估、报告与 PDF 导出、Skill / JD 解析 | `/api/simulation` |
| `knowledgebase` 知识库 | 文件上传、分类、向量化（pgvector）、检索、同步 / SSE 流式问答、会话式 RAG Chat | `/api/knowledgebase`、`/api/rag-chat` |
| `schedule` 日程 | 邀请文本解析（规则优先 + LLM 兜底）、日程 CRUD、状态定时更新 | `/api/schedule`、`/api/simulation-schedule` |
| `voice` 语音交互 | WebSocket 实时通话（ASR → LLM → TTS）、会话与消息持久化、异步评估 | `/api/voice`、`/ws/voice-interview/{sessionId}` |

## 技术栈

### 后端

| 技术 | 版本 | 说明 |
| --- | --- | --- |
| Spring Boot | 4.0.1 | 应用框架 |
| Java | 21 | 开发语言，启用虚拟线程 |
| Spring AI | 2.0.0-M4 | OpenAI-compatible 集成与向量能力 |
| PostgreSQL + pgvector | 16 + pgvector | 关系数据库与向量检索（1024 维，HNSW / COSINE） |
| Redis + Redisson | 7 + 4.0.0 | 缓存、限流、Redis Stream 异步任务 |
| Apache Tika | 2.9.2 | 文档解析 |
| iText | 8.0.5 | PDF 导出 |
| MapStruct | 1.6.3 | Entity / DTO 映射 |
| DashScope SDK | 2.22.7 | Qwen 实时语音 ASR / TTS |
| Maven | 3.9+ | 构建工具（容器内使用 3.9.11） |

### 前端

| 技术 | 版本 | 说明 |
| --- | --- | --- |
| React | 18.3 | UI 框架 |
| TypeScript | 5.6 | 开发语言 |
| Vite | 5.4 | 构建工具 |
| TailwindCSS | 4.1 | 样式方案 |
| React Router | 7 | 路由 |
| axios / recharts / framer-motion | - | 请求、图表与动效 |
| pnpm | 10.26 | 包管理器（`packageManager` 已锁定） |

### 基础设施

- **PostgreSQL 16 + pgvector**：业务数据 + 知识库向量（1024 维，HNSW，COSINE）。
- **Redis 7**：缓存、限流（Lua 滑动窗口）与 4 条 Redis Stream 异步管道。
- **S3 兼容对象存储**：根目录 `docker-compose.yml` 使用 MinIO；`docker-compose.dev.yml` 使用 RustFS；k8s 部署使用 MinIO。后端通过统一的 S3 接口访问。
- **Nginx**：前端静态资源 + `/api`（含 SSE）与 `/ws`（语音 WebSocket）反向代理。

## 业务结构说明

从产品语义上，`Ruici` 的业务结构应这样理解：

| 平台语义 | 当前代码承载 |
| --- | --- |
| `document` 文档分析 | `modules/document` |
| `simulation` 情景模拟 | `modules/simulation` |
| `knowledgebase` 知识库 | `modules/knowledgebase` |
| `schedule` 日程场景 | `modules/schedule` |
| `voice` 语音交互 | `modules/voice` |

部分配置键、数据库字段和类名仍保留旧项目命名（如 `Resume*`、`Interview*`、`voice-interview`），这是重构期的兼容层，不代表最终业务语义：

| 现有键名 / 目录名 | 现在真正的语义 |
| --- | --- |
| `app.resume` / `resume` | 通用职业文档分析 |
| `app.interview` / `interview` | 情景模拟主流程 |
| `app.voice-interview` | 语音交互 / 语音情景模拟 |
| `APP_VOICE_INTERVIEW_LLM_PROVIDER` | 语音模块默认 Provider |

前后端对接时，优先查看 `api/` 下的模块接口说明，而不是只根据类名猜测语义。

## 项目结构

```text
ruici-ai-platform/
├── src/
│   ├── main/java/com/ruici/ai/
│   │   ├── common/                # 通用能力：异常、限流、Provider 路由、Redis Stream
│   │   ├── infrastructure/        # 文件、导出、Redis、映射等基础设施
│   │   └── modules/
│   │       ├── document/          # 文档上传、解析、分析、导出
│   │       ├── simulation/        # 情景模拟、题目生成、回答评估
│   │       ├── knowledgebase/     # 知识库上传、向量化、RAG 问答
│   │       ├── schedule/          # 场景化日程解析与管理
│   │       └── voice/             # 语音交互 / ASR / TTS
│   ├── main/resources/            # application.yml、logback、prompts、skills、fonts、scripts
│   └── test/                      # JUnit 5 测试与测试资源
├── frontend/                      # React 18 + TypeScript + Vite + TailwindCSS
│   ├── public/
│   └── src/                       # api / components / pages / hooks / utils / types / constants
├── docker/
│   └── postgres/init.sql          # 数据库初始化（创建 vector 扩展）
├── k8s/                           # 集群实际使用清单：app、Ingress、Prometheus 监控告警
├── deploy/k8s/                    # 一键部署套件：deploy.sh、deploy.env.example、manifests
├── api/                           # 模块接口文档（documents / simulation / knowledgebase / rag-chat / schedule / voice）
├── word/                          # 版本测试报告与问题记录
├── Dockerfile                     # 后端多阶段构建（Maven + JRE 21）
├── docker-compose.yml             # 完整本地编排（应用 + 中间件 + 自动建 bucket）
├── docker-compose.dev.yml         # 仅启动基础设施（PostgreSQL / Redis / RustFS）
├── Jenkinsfile                    # Jenkins CI/CD 流水线
├── .gitlab-ci.yml                 # GitLab CI 占位流水线（当前仅示例）
├── settings.xml                   # Maven 阿里云镜像配置（容器构建用）
├── .env.example                   # 环境变量模板（复制为 .env）
├── .dockerignore
├── .gitignore
├── pom.xml
├── SETUP_API_KEYS.md
├── CLAUDE.md
├── CODEX_CLAUDE_RULES.md
└── README.md
```

> 说明：`src/main/resources/application-dev.yml`、`application-local.*`、`.env` 等属于本地覆盖配置，已被 `.gitignore` 忽略，不随仓库分发；需要时请从 `.env.example` 复制并按环境填写。

## 快速开始

### 1. 环境要求

| 依赖 | 版本 | 必需 | 说明 |
| --- | --- | --- | --- |
| JDK | 21+ | 是 | 后端运行环境 |
| Maven | 3.9+ | 是 | 后端构建工具 |
| Node.js | 18+ | 是 | 前端构建环境 |
| pnpm | 10.x | 是 | 前端包管理器 |
| Docker + Compose | 最新版 | 推荐 | 本地启动 PostgreSQL / Redis / 对象存储 |

启动前先明确 4 个初始化职责：

- **API Key**：由你在 `.env` 或环境变量中配置。
- **数据库 `ruici_ai_platform`**：`docker compose` 会自动创建；手动安装 PostgreSQL 时需自己创建。
- **数据表**：由 `spring.jpa.hibernate.ddl-auto=update` 自动补齐（`ddl-auto` 只能建表，不能建库）。
- **对象存储 bucket**：完整 `docker-compose.yml` 会自动创建；`docker-compose.dev.yml` 需要手动创建 `ruici-ai-platform`。

### 2. 配置环境变量

```bash
cp .env.example .env
```

至少配置两类 Provider：

- **第三方 OpenAI-compatible 中转**：默认聊天与情景模拟。
- **DashScope / Qwen**：向量化与实时语音。

核心变量：

```bash
AI_DEFAULT_PROVIDER=third-party
THIRD_PARTY_BASE_URL=https://api-s.zwenooo.link/v1
THIRD_PARTY_API_KEY=your_gateway_key
THIRD_PARTY_MODEL=gpt5.2
THIRD_PARTY_FALLBACK_MODEL=qwen-plus

AI_BAILIAN_API_KEY=your_dashscope_key
AI_EMBEDDING_MODEL=text-embedding-v3
APP_VOICE_INTERVIEW_LLM_PROVIDER=dashscope
```

> `THIRD_PARTY_MODEL` 以 `.env.example` 的 `gpt5.2` 为准；代码内默认值为 `gpt-5.2`。不同中转对模型名的写法可能不同，请以服务商文档为准。

完整字段见 `.env.example` 与 `SETUP_API_KEYS.md`。

> 安全提醒：真实密钥只放在 `.env` 或环境变量中，严禁写入 `README`、`SETUP_API_KEYS.md`、测试用例或任何会进入版本库的文件。

#### 安全数据区（基于 `.gitignore`）

以下内容默认不进入版本控制：

- 环境变量与密钥：`.env`、`.env.*`（仅保留 `.env.example`）。
- 本地 Spring 覆盖配置：`application-local.*`、`application-dev-local.*`、`bootstrap-local.*`。
- 证书 / 私钥 / Keystore：`*.pem`、`*.key`、`*.p12`、`*.pfx`、`*.jks`、`id_rsa` 等。
- 内部资料与提示词草案：`private/`。
- 工具缓存（可能携带 token / 会话态）：`.playwright-mcp/`、`.playwright-cli/`。
- 一键部署配置：`deploy/k8s/deploy.env`、`deploy/k8s/.rendered/`。

`.gitignore` 还默认忽略 `src/main/resources/application-dev.yml`。如需团队共享开发配置，建议提交不含密钥的 `application-dev.example.yml`，本地用 `application-dev.yml` 覆盖。

### 3. 启动基础设施

#### 方案 A：仅启动依赖（推荐本地开发）

```bash
docker compose -f docker-compose.dev.yml up -d
```

该编排包含：

| 服务 | 镜像 | 端口 | 说明 |
| --- | --- | --- | --- |
| `postgres` | `pgvector/pgvector:pg16` | 5432 | 自动创建 `ruici_ai_platform` 与 `vector` 扩展 |
| `redis` | `redis:7` | 6379 | 缓存与 Redis Stream |
| `rustfs` | `rustfs/rustfs:latest` | 9000 / 9001 | S3 兼容对象存储，控制台在 9001 |

注意事项：

- **必须先有 `.env`**：dev 编排的 `POSTGRES_PASSWORD` 默认是 `123456`，而应用默认是 `password`；直接 `cp .env.example .env` 后两边会统一为 `password`。
- **RustFS 默认账号**为 `rustfsadmin / rustfsadmin`（可用 `RUSTFS_ACCESS_KEY`、`RUSTFS_SECRET_KEY` 覆盖）。应用侧 `APP_STORAGE_ACCESS_KEY` / `APP_STORAGE_SECRET_KEY` 必须与之一致，否则连接对象存储会 403。
- **RustFS 不会自动建 bucket**：浏览器打开 `http://localhost:9001` 登录后，手动创建 `ruici-ai-platform`。

#### 方案 B：完整编排（应用 + 中间件）

```bash
docker compose up -d --build
```

该编排使用 MinIO + `createbuckets` 初始化容器，自动创建并公开读 `ruici-ai-platform` bucket；前端通过 `http://localhost` 访问，后端为 `http://localhost:8080`。

#### 3.1 手动安装 PostgreSQL 时

```sql
CREATE DATABASE ruici_ai_platform;
\c ruici_ai_platform
CREATE EXTENSION IF NOT EXISTS vector;
```

`docker/postgres/init.sql` 只负责创建 `vector` 扩展，不负责建库。

### 4. 启动后端

```bash
mvn spring-boot:run
```

启动失败时优先检查：

1. 当前 Java 版本是否为 **21**。
2. `.env` 中的 `THIRD_PARTY_API_KEY`、`AI_BAILIAN_API_KEY` 是否已配置。
3. PostgreSQL 中是否存在数据库 `ruici_ai_platform`。
4. PostgreSQL 是否已启用 `vector` 扩展。
5. 对象存储中是否已存在 bucket `ruici-ai-platform`，且 AccessKey / SecretKey 与服务端一致。

后端默认地址：`http://localhost:8080`，Swagger：`http://localhost:8080/swagger-ui.html`。

常用配置默认值：

| 配置 | 默认值 | 说明 |
| --- | --- | --- |
| `server.port` | `8080` | 后端端口 |
| `spring.servlet.multipart.max-file-size` | `50MB` | 上传大小限制 |
| `spring.jpa.hibernate.ddl-auto` | `update` | 开发环境自动补表 |
| `spring.ai.vectorstore.pgvector.dimensions` | `1024` | 与 `text-embedding-v3` 对齐 |
| `spring.ai.vectorstore.pgvector.index-type` | `HNSW` | 向量索引 |
| `spring.ai.vectorstore.pgvector.distance-type` | `COSINE_DISTANCE` | 距离度量 |
| Redisson database | `2` | 与业务 DB 隔离 |

### 5. 启动前端

```bash
cd frontend
pnpm install
pnpm dev
```

前端默认地址：`http://localhost:5173`。

## 部署方式

| 方式 | 适用场景 | 入口 | 说明 |
| --- | --- | --- | --- |
| 本地开发编排 | 本机开发 | `docker-compose.dev.yml` | 只启动 PostgreSQL / Redis / RustFS |
| 完整 Compose | 单机演示 / 验收 | `docker-compose.yml` | 应用 + 中间件 + 自动建 bucket |
| 集群清单 + Jenkins | 现有集群滚动发布 | `k8s/`、`Jenkinsfile` | kompose 生成的清单；Jenkins 构建镜像并 `kubectl set image` |
| 一键部署套件 | 项目拷走后快速上线 | `deploy/k8s/deploy.sh` | 自动构建、下发配置、部署与等待就绪 |

### Kubernetes 一键部署

```bash
cd deploy/k8s
cp deploy.env.example deploy.env   # 首次可先填写 AI Key，其余默认值即可
bash deploy.sh
```

脚本会完成：

1. 检查 `kubectl` 与集群连通性，打印当前 context。
2. 构建后端 / 前端镜像；`IMAGE_PREFIX` 为空时使用本地镜像模式（`docker save` + `ctr images import`），填写仓库地址时自动 `docker push`。
3. 创建 `ruici-app-config`、`ruici-app-secret`、`postgres-init`、`frontend-nginx` 等 ConfigMap / Secret。
4. 部署 PostgreSQL（pgvector）、Redis、MinIO、后端与前端，等待 rollout 完成。
5. 输出 NodePort / LoadBalancer / Ingress 访问地址。

常用命令：

```bash
bash deploy.sh --skip-build   # 只更新配置与工作负载
bash deploy.sh --build-only   # 只构建镜像
bash deploy.sh --delete        # 卸载（会删除 PVC 数据）
```

多节点集群注意：本地镜像导入只对执行脚本的节点生效；其他节点需重复导入，或将 `IMAGE_PREFIX` 指向 Harbor 等所有节点可访问的仓库。

详细参数、资源清单与排障见 [`deploy/k8s/README.md`](deploy/k8s/README.md)。

## 运维体系与团队分工

### 团队构成

项目由 6 人团队协作完成：

| 角色 | 人数 | 主要职责 |
| --- | --- | --- |
| 前端 | 2 | React 页面开发、交互实现与联调 |
| 后端 | 2 | 业务模块、AI 能力接入与接口实现 |
| 运维 | 2 | 集群与部署、CI/CD、网络与证书、监控告警、线上故障处理 |

本人属于运维组，负责并实际参与上述运维工作的大部分内容：集群与容器化部署、CI/CD 流水线、镜像仓库与凭据、Ingress / 网络 / 证书、监控告警，以及线上故障的定位与恢复；同时为前后端联调提供环境、网络与发布支持。

### 基础设施与部署

- **Kubernetes 集群**：kubeadm 部署，控制面与工作节点分离，容器运行时为 containerd；处理过 kubelet / docker / containerd 并存环境下的兼容问题。
- **两套清单并存**：
  - `k8s/`：集群实际使用的清单，由 `kompose convert -f docker-compose.yml` 生成，包含应用、中间件、Ingress 与 Prometheus 监控告警；
  - `deploy/k8s/`：本项目新增的一键部署套件，覆盖 PostgreSQL(pgvector)、Redis、MinIO、后端、前端与可选 Ingress。
- **中间件**：PostgreSQL 16 + pgvector、Redis 7（缓存 + Redis Stream）、MinIO（S3 兼容）均完成容器化、持久化与健康检查配置。
- **访问链路**：Nginx Ingress → 前端 Nginx → 后端 Service，统一处理静态资源、`/api`（含 SSE 流式响应）与 `/ws`（语音 WebSocket）。

### CI/CD 与镜像管理

- Jenkins 流水线（`Jenkinsfile`）完成镜像构建、Docker Hub 登录与推送、`kubectl set image` 滚动发布，并通过 `kubectl rollout status` 校验发布结果；镜像标签使用 `构建号-提交短 SHA`，便于回滚定位。
- 集群通过 `imagePullSecrets`（`regcred`，由 `kubectl create secret docker-registry` 创建）注入仓库凭据，不随仓库提交。
- 构建阶段使用阿里云 Maven 镜像（`settings.xml`），规避容器网络下访问 Maven Central 的 TLS 握手问题。
- `deploy/k8s/deploy.sh` 支持离线部署：本地构建镜像后直接导入节点 containerd，不依赖外部镜像仓库。
- `.gitlab-ci.yml` 当前为占位示例，实际发布以 Jenkins 流水线为准。

### 网络、证书与稳定性

- 处理过容器 / 宿主机 DNS 异常、Docker Hub 访问超时、客户端代理与 Docker daemon proxy 配置等问题，保证镜像构建与拉取链路稳定。
- 证书统一在 Ingress 与镜像仓库侧管理，负责证书导入、更新与有效性校验。
- 通过健康检查、滚动更新、失败重试、`restartPolicy` 与数据卷持久化保障服务可用性。

### 监控与告警

- `k8s/servicemonitor.yaml`、`k8s/prometheusRule.yaml`、`k8s/alertmanege.yaml` 接入 Prometheus Operator，采集服务指标并配置告警规则。
- 重点关注 Pod 就绪状态、重启次数、接口错误率与资源使用率，异常时通过 Alertmanager 通知并跟进处理。

### 典型故障处理记录

| 问题 | 现象 | 处理方式 |
| --- | --- | --- |
| 证书误导入 | 服务访问异常、证书校验失败 | 定位错误证书来源，回滚误导入的证书并重新导入正确证书，恢复服务（该问题由团队另一名运维同学在操作中引入，本人完成排查与修复） |
| 代理配置更新卡死 | 客户端提示 `configuration update is already running` | 清理残留核心进程与配置校验锁，恢复配置更新与代理可用性 |
| 镜像拉取超时 | `docker login` / `docker pull` 连接 Docker Hub 超时 | 为 Docker daemon 配置代理与 DNS，逐项验证 `auth.docker.io`、`registry-1.docker.io` 连通性 |
| 集群资源定位 | 执行 DaemonSet 更新时提示资源不存在 | 按命名空间与资源类型确认真实部署位置，修正运维命令并完成操作 |

> 说明：上述为本人参与处理的典型问题；其中「证书误导入」并非本人操作引入，由本人负责定位与修复。

## API 文档

接口总览见 [`api/README.md`](api/README.md)，按模块拆分：

| 前缀 | 模块 | 文档 |
| --- | --- | --- |
| `/api/documents` | 文档分析 | `api/documents.md` |
| `/api/simulation` | 情景模拟 | `api/simulation.md`、`api/simulation-skills.md` |
| `/api/knowledgebase` | 知识库 | `api/knowledgebase.md` |
| `/api/rag-chat` | 会话式 RAG | `api/rag-chat.md` |
| `/api/schedule`（兼容 `/api/simulation-schedule`） | 日程 | `api/schedule.md` |
| `/api/voice`（兼容 `/api/voice-interview`） | 语音交互 | `api/voice.md` |

返回约定：

- 普通接口统一返回 `Result<T>`；业务异常经 `GlobalExceptionHandler` 转为 HTTP 200 + 业务错误码。
- 文件导出接口返回二进制流。
- SSE 接口直接返回流式响应，不再包裹 `Result<T>`。

## 常见问题

### 向量化为什么仍然依赖 Qwen / DashScope？

当前项目保留 `Qwen / DashScope` 作为稳定的 Embedding 与实时语音来源，这部分不跟随主聊天 Provider 切换。向量维度固定为 1024，与 `text-embedding-v3` 对齐。

### 为什么默认不直接用 OpenAI？

项目主策略是 **自定义 OpenAI-compatible 中转优先**，OpenAI 原生 Provider 作为可选项保留在多 Provider 注册表中。

### OpenAI-compatible 一定完全兼容吗？

不一定。不同网关在 `/chat/completions`、流式事件结构、工具调用和结构化输出上可能存在差异。项目已按 Provider 分层：聊天默认走中转，语音 / 向量保留 Qwen，并对结构化输出做重试。

### 本地用 dev 编排后，上传文件报 403 / 连接对象存储失败？

`docker-compose.dev.yml` 用的是 **RustFS**，不是 MinIO：

- 默认账号是 `rustfsadmin / rustfsadmin`，控制台在 `http://localhost:9001`；
- `.env` 中的 `APP_STORAGE_ACCESS_KEY` / `APP_STORAGE_SECRET_KEY` 必须与 `RUSTFS_ACCESS_KEY` / `RUSTFS_SECRET_KEY` 一致；
- bucket `ruici-ai-platform` 需要手动创建。

### 后端连不上数据库？

先确认 `.env` 存在。dev 编排的 `POSTGRES_PASSWORD` 默认是 `123456`，应用默认是 `password`；只要按 `cp .env.example .env` 启动，两边会统一。若使用自定义密码，PostgreSQL 数据卷初始化后不会因改配置而变更，需进入容器执行 `ALTER USER`。

### 语音通话连不上 WebSocket？

部署在 Nginx / Ingress 后面时，需要：

- 代理 `/ws/` 并透传 `Upgrade` / `Connection` 头；
- 透传 `Host`（含端口）与 `X-Forwarded-Proto`，后端才能生成正确的 `ws://` / `wss://` 地址；
- 后端开启 `server.forward-headers-strategy=framework`。

`deploy/k8s/frontend-nginx.conf` 已包含上述配置。

### Kubernetes 部署常见问题？

PVC Pending、ImagePullBackOff、CrashLoopBackOff、修改数据库密码失效等，统一见 [`deploy/k8s/README.md`](deploy/k8s/README.md) 的“常见问题”章节。

## 内部说明

`private/项目重构提示词-优化.md` 是当前重构的设计基线之一。对外文档以本 `README` 为准，对内设计可继续沿 `private/` 目录推进。

`api/README.md` 汇总了当前后端接口入口，供前端逐步迁移使用。