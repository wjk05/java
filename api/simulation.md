# 情景模拟接口

控制器：`src/main/java/com/ruici/ai/modules/simulation/InterviewController.java`

## 1. 列出会话

- `GET /api/simulation/sessions`
- 用途：获取情景模拟会话列表，给记录页或继续练习入口使用。

## 2. 创建会话

- `POST /api/simulation/sessions`
- 用途：创建新的情景模拟会话。
- 前端 2.0 主流程：由“情景方向 + 练习主题 + 是否基于文档 + 题目数量/语音时长”组成统一配置，再透传到创建会话接口。
- 兼容行为：当传入 `resumeId` 且未设置 `forceCreate=true` 时，后端会优先尝试恢复“同一文档 + 同一场景 + 同一模板”的未完成会话，而不是简单按文档全局复用。
- 限流：`GLOBAL=5`，`IP=5`
- 关键请求字段：
  - `simulationDirection`：2.0 新增统一方向字段，支持 `JOB_INTERVIEW`、`PROFESSIONAL_QA`、`WORKPLACE_COMMUNICATION`
  - `scenarioType`：场景类型，当前已支持 `job-interview`、`tcm-qa`、`novel-expert`
  - `simulationDifficulty`：2.0 新增统一难度字段，支持 `EASY`、`NORMAL`、`SHARP`
  - `skillId`：技能模板 ID，例如 `java-backend`
  - `difficulty`：难度级别
  - `questionCount`：题目数量
  - `basedOnDocument`：是否显式基于文档开启模拟；当前前端已提供独立开关，不再只依赖 `resumeId` 是否为空来推断
  - `resumeId` / `resumeText`：文档关联信息
  - `customCategories` / `jdText`：自定义场景（可选，JD 解析结果 + 原文）
  - `llmProvider`：可选，指定 Provider
  - `forceCreate`：可选，强制创建新会话（默认 `false`）
- 返回：`InterviewSessionDTO`

## 3. 获取会话详情

- `GET /api/simulation/sessions/{sessionId}`
- 用途：获取当前会话整体状态。

## 4. 获取当前题目

- `GET /api/simulation/sessions/{sessionId}/question`
- 用途：获取当前主问题 / 追问的展示内容。

## 5. 提交答案并推进

- `POST /api/simulation/sessions/{sessionId}/answers`
- 限流：`GLOBAL=10`
- 请求体：
  - `questionIndex`
  - `answer`
- 用途：提交当前答案，并根据服务端逻辑推进到下一题或结束。

## 6. 暂存答案

- `PUT /api/simulation/sessions/{sessionId}/answers`
- 请求体：
  - `questionIndex`
  - `answer`
- 用途：只保存，不推进题目。

## 7. 手动完成会话

- `POST /api/simulation/sessions/{sessionId}/complete`
- 用途：提前交卷，进入完成状态。

## 8. 获取评估报告

- `GET /api/simulation/sessions/{sessionId}/report`
- 用途：根据问答记录生成结构化评估报告。

## 9. 获取历史详情

- `GET /api/simulation/sessions/{sessionId}/details`
- 用途：获取历史详情页数据。
- 关键返回字段：
  - `simulationDirection`
  - `simulationDifficulty`
  - `basedOnDocument`
  - `questionCount`
  - `skillId`

## 10. 导出 PDF

- `GET /api/simulation/sessions/{sessionId}/export`
- 用途：导出情景模拟报告 PDF。
- 返回：`application/pdf`

## 11. 查找未完成会话

- `GET /api/simulation/sessions/unfinished/{resumeId}`
- 用途：当某份文档已存在未完成会话时，前端可先恢复而不是重复创建。

## 12. 删除会话

- `DELETE /api/simulation/sessions/{sessionId}`
- 用途：删除会话及其关联持久化数据。
