# 知识库接口

控制器：`src/main/java/com/ruici/ai/modules/knowledgebase/KnowledgeBaseController.java`

## 1. 获取知识库列表

- `GET /api/knowledgebase/list`
- 可选参数：
  - `sortBy`
  - `vectorStatus`
    - 可选值：`PENDING`、`PROCESSING`、`COMPLETED`、`FAILED`（不区分大小写，服务端会转为大写）
    - 传入非法值会返回 `Result.error("无效的向量化状态: ...")`

## 2. 获取详情

- `GET /api/knowledgebase/{id}`

## 3. 删除知识库

- `DELETE /api/knowledgebase/{id}`

## 4. 普通问答

- `POST /api/knowledgebase/query`
- 用途：基于多个知识库执行一次同步问答。
- 限流：`GLOBAL=10`，`IP=10`

## 5. 流式问答（SSE）

- `POST /api/knowledgebase/query/stream`
- 返回：`text/event-stream`
- 限流：`GLOBAL=5`，`IP=5`

## 6. 分类相关

- `GET /api/knowledgebase/categories`
- `GET /api/knowledgebase/category/{category}`
- `GET /api/knowledgebase/uncategorized`
- `PUT /api/knowledgebase/{id}/category`

## 7. 上传下载

- `POST /api/knowledgebase/upload`
- `Content-Type: multipart/form-data`
- 表单字段：
  - `file`
  - `name`（可选）
  - `category`（可选）
- 限流：`GLOBAL=3`，`IP=3`
- `GET /api/knowledgebase/{id}/download`

## 8. 搜索与统计

- `GET /api/knowledgebase/search?keyword=...`
- `GET /api/knowledgebase/stats`

## 9. 重新向量化

- `POST /api/knowledgebase/{id}/revectorize`
- 限流：`GLOBAL=2`，`IP=2`
