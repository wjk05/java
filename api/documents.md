# 文档分析接口

控制器：`src/main/java/com/ruici/ai/modules/document/ResumeController.java`

## 1. 上传并分析文档

- `POST /api/documents/upload`
- `Content-Type: multipart/form-data`
- 表单字段：
  - `file`
  - `profession`（可选，职业/岗位/专业方向）
  - `analysisDifficulty`（可选，`EASY / NORMAL / SHARP`，默认 `NORMAL`）
- 用途：上传职业文档并触发异步分析（分析任务通过 Redis Stream 入队）。
- 限流：`GLOBAL=5`，`IP=5`
- 业务约束：服务层默认限制文件大小 `10MB`；允许的 `Content-Type` 由配置 `app.resume.allowed-types` 控制。
- 返回：`Result<Map<String, Object>>`
  - `duplicate=false`：返回 `resume`（含 `id/filename/analyzeStatus=PENDING`）与 `storage`（含 `fileKey/fileUrl/resumeId`）
  - `duplicate=true`：优先返回 `analysis`（历史 `DocumentAnalysisResponse`）；若无历史分析则返回 `resume` 状态 + `storage`

## 2. 获取文档列表

- `GET /api/documents`
- 用途：获取已上传文档列表。
- 关键返回字段：
  - `profession`：当前文档最近一次分析所绑定的职业/岗位/专业方向
  - `analysisDifficulty`：最近一次分析所绑定的分析力度，取值 `EASY / NORMAL / SHARP`
  - `analyzeStatus` / `analyzeError`：异步分析状态与失败原因
  - `latestScore`、`interviewCount`：用于文档列表和后续情景模拟入口展示

## 3. 获取文档详情

- `GET /api/documents/{id}/detail`
- 用途：获取文档详情和分析历史。
- 关键返回字段：
  - 顶层 `profession`
  - 顶层 `latestAnalysisDifficulty`
  - `analyses[*].analysisDifficulty`
  - `interviews[*]`：关联的情景模拟历史

## 4. 导出分析报告

- `GET /api/documents/{id}/export`
- 用途：导出文档分析报告 PDF（二进制流）。
- 返回：`application/pdf`

## 5. 删除文档

- `DELETE /api/documents/{id}`
- 用途：删除文档及其关联记录。

## 6. 重新分析

- `POST /api/documents/{id}/reanalyze`
- 用途：手动触发重新分析。
- 兼容扩展：支持可选查询参数 `profession`、`analysisDifficulty`，用于按新的分析视角重新触发分析。
- 前端 2.0 约定：详情页会把用户当前输入的 `profession` 与 `analysisDifficulty` 直接透传给该接口，用于覆盖本次重分析视角。
- 限流：`GLOBAL=2`，`IP=2`

## 7. 健康检查

- `GET /api/documents/health`
- 用途：提供文档模块状态检查。
