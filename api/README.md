# Ruici AI Platform 接口总览

本目录用于沉淀后端 Controller 接口，方便前端在后续重构时按模块对接。

## 模块索引

- `simulation.md`：情景模拟主流程接口
- `simulation-skills.md`：情景模拟技能与 JD 解析接口
- `documents.md`：通用职业文档接口
- `voice.md`：语音交互接口
- `schedule.md`：场景日程接口
- `knowledgebase.md`：知识库管理与检索接口
- `rag-chat.md`：RAG 聊天会话接口

## 当前兼容约定

- `simulation` 模块仍保留部分 `Interview*` 类名，但对外语义已经按“多场景情景模拟”理解。
- 当前前端 2.0 主流程已经显式透传 `simulationDirection`、`simulationDifficulty`、`questionCount`、`basedOnDocument` 等统一字段。
- `voice` 模块同时支持 `/api/voice` 与 `/api/voice-interview` 两套入口，推荐新前端优先使用 `/api/voice`。
- `schedule` 模块同时支持 `/api/schedule` 与 `/api/simulation-schedule` 两套入口，推荐新前端优先使用 `/api/schedule`。
- `schedule` 结构化返回体当前仍复用历史字段名 `interviewTime` / `interviewType`，这是兼容层，不代表只服务于面试场景。
- `documents` 模块对外已经统一走 `/api/documents`，但内部类名仍保留 `Resume*` 历史命名。
- `documents` 模块当前前端已显式使用 `profession` 与 `analysisDifficulty` 两个 2.0 字段做上传与重分析。

## 返回约定

- 普通接口统一返回 `Result<T>`。
- 文件导出接口返回二进制流（例如 PDF、知识库原文件下载）。
- SSE 接口直接返回流式响应，不再包裹 `Result<T>`。
