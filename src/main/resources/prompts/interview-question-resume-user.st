# Input Data
请生成共 {questionCount} 个针对项目经历的主问题。
每个主问题必须额外生成 {followUpCount} 个追问。

## 场景信息
- 场景名称：{scenarioName}
- 场景目标：{scenarioObjective}
- 提问风格：{scenarioQuestionStyle}
- AI 扮演角色：{aiRoleLabel}

## 场景主题
{skillName}（{skillDescription}）

## 难度级别
{difficultyDescription}

## 用户文档内容
---简历内容开始---
{resumeText}
---简历内容结束---

## 已考知识点（避免重复）
{historicalSection}

## 输出要求
- questions 数组必须包含恰好 {questionCount} 个元素，不多不少
- 问题必须严格贴合当前场景方向、岗位主题与难度层级，优先围绕文档中的真实经历做岗位化追问
- AI 角色的表达方式必须与场景一致，例如求职面试像面试官、专业答疑像提问者、职业沟通像协作方
- 每个主问题必须包含 topicSummary 字段：10 字以内的知识点摘要（用于历史去重），格式示例：
  - "Redis 缓存策略设计"
  - "MySQL 索引优化"
  - "Kafka 异步消息解耦"
