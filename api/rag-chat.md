# RAG 聊天接口

控制器：`src/main/java/com/ruici/ai/modules/knowledgebase/RagChatController.java`

## 1. 创建会话

- `POST /api/rag-chat/sessions`

## 2. 获取会话列表

- `GET /api/rag-chat/sessions`

## 3. 获取会话详情

- `GET /api/rag-chat/sessions/{sessionId}`

## 4. 更新标题

- `PUT /api/rag-chat/sessions/{sessionId}/title`

## 5. 切换置顶状态

- `PUT /api/rag-chat/sessions/{sessionId}/pin`

## 6. 更新关联知识库

- `PUT /api/rag-chat/sessions/{sessionId}/knowledge-bases`

## 7. 删除会话

- `DELETE /api/rag-chat/sessions/{sessionId}`

## 8. 发送消息（流式）

- `POST /api/rag-chat/sessions/{sessionId}/messages/stream`
- 返回：`text/event-stream`
- 说明：服务端会先落库用户消息与 AI 占位消息，再流式返回内容，结束后回写完整答案。
- 输出格式：使用 SSE 的 `data:` 事件返回文本分片；服务端会对换行符做转义以避免破坏 SSE 帧格式。
