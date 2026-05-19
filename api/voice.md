# 语音交互接口

控制器：`src/main/java/com/ruici/ai/modules/voice/controller/VoiceInterviewController.java`

## 路径兼容

- 推荐新前端使用：`/api/voice`
- 兼容旧入口：`/api/voice-interview`

下面所有路径都以两套前缀同时生效。

## 1. 创建语音会话

- `POST /sessions`
- 关键请求字段：
  - `skillId`：语音场景模板 ID
  - `roleType`：历史兼容字段；如果旧调用方仍在传，后端会继续接收，但新调用方应优先使用 `skillId`
  - `difficulty`：难度
  - `llmProvider`：可选 Provider
  - `plannedDuration`：预计时长
  - `introEnabled` / `techEnabled` / `projectEnabled` / `hrEnabled`：阶段开关
- 用途：创建新的语音场景会话。

## 2. 获取会话详情

- `GET /sessions/{sessionId}`

## 3. 结束会话

- `POST /sessions/{sessionId}/end`
- 用途：结束会话并触发异步评估。

## 4. 暂停会话

- `PUT /sessions/{sessionId}/pause`
- 请求体可包含：`reason`

## 5. 恢复会话

- `PUT /sessions/{sessionId}/resume`

## 6. 获取会话列表

- `GET /sessions`
- 可选查询参数：
  - `userId`
  - `status`

## 7. 删除会话

- `DELETE /sessions/{sessionId}`

## 8. 获取消息记录

- `GET /sessions/{sessionId}/messages`

## 9. 获取评估结果

- `GET /sessions/{sessionId}/evaluation`
- 用途：轮询异步评估状态。

## 10. 触发评估

- `POST /sessions/{sessionId}/evaluation`
- 用途：手动触发异步评估；若已完成则直接返回缓存结果。

## WebSocket 协议补充

- 推荐控制动作：`end_session`
- 兼容旧控制动作：`end_interview`
- 其他动作：`submit`、`start_phase`
- 连接成功后，服务端会先发送 `welcome` 控制消息，再根据模板自动下发开场文本 / 音频。
