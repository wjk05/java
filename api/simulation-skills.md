# 情景模拟技能接口

控制器：`src/main/java/com/ruici/ai/modules/simulation/skill/InterviewSkillController.java`

## 1. 获取技能列表

- `GET /api/simulation/skills`
- 用途：拉取可选场景模板 / 技能模板。

## 2. 获取单个技能详情

- `GET /api/simulation/skills/{id}`
- 用途：获取单个技能的完整定义。

## 3. 解析 JD 为分类

- `POST /api/simulation/skills/parse-jd`
- 限流：`IP=5`
- 请求体：
  - `jdText`
- 用途：将岗位 JD 文本解析为前端可编辑的分类列表，常用于自定义技能生成。
