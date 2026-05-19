# 🔑 AI Provider 配置指南

`Ruici-AI-Platform` 采用多 Provider 架构，但默认策略不是再把 `Qwen / DashScope` 当成唯一入口。

当前建议的接入方式是：

- **第三方 OpenAI-compatible 中转**：作为默认聊天 / 通用推理 / 情景模拟 Provider
- **DashScope / Qwen**：保留给向量化、实时语音 ASR/TTS，以及不可替代的专项能力

## 📋 需要准备哪些 Key

### 1. 第三方 OpenAI-compatible 中转（默认主入口）

**用途**:
- 通用聊天
- 情景模拟
- 默认专项能力推理
- 未来可扩展到更多文本场景

**推荐配置**:

```bash
AI_DEFAULT_PROVIDER=third-party
THIRD_PARTY_BASE_URL=https://api-s.zwenooo.link/v1
THIRD_PARTY_API_KEY=your_gateway_key
THIRD_PARTY_MODEL=gpt-5.2
THIRD_PARTY_FALLBACK_MODEL=qwen-plus
```

**说明**:
- 这里接的是 OpenAI-compatible 网关，不保证所有行为与 OpenAI 原生完全一致。
- 如果你启用了流式响应、结构化输出或工具调用，请按实际网关能力验证。
- 项目已经把主聊天路由和语音/向量路由分开，避免一个 Provider 承担全部能力。

---

### 2. 阿里云百炼 AI（DashScope / Qwen）

**用途**:
- **Embedding 向量化**: 知识库分块向量化（`text-embedding-v3`）
- **ASR 语音识别**: 实时语音转文本（`qwen3-asr-flash-realtime`）
- **TTS 语音合成**: 实时文本转语音（`qwen3-tts-flash-realtime`）

**统一 API Key**: 一个 DashScope Key 即可覆盖上述能力。

**获取步骤**:
1. 访问 [阿里云百炼平台](https://bailian.console.aliyun.com/)
2. 登录/注册阿里云账号
3. 开通 DashScope 服务（有免费额度）
4. 创建 API Key
5. 复制 API Key

**配置变量**:
```bash
AI_BAILIAN_API_KEY=sk-xxxxxxxxxxxxxxxxxxxxxxxx
AI_EMBEDDING_MODEL=text-embedding-v3
APP_VOICE_INTERVIEW_LLM_PROVIDER=dashscope
```

> `APP_VOICE_INTERVIEW_LLM_PROVIDER` 仍然沿用旧环境变量名，但现在实际控制的是整个 `voice` 模块默认 Provider，而不只是“语音面试”。

**费用**:
- 新用户有免费额度
- Embedding（`text-embedding-v3`）: 以阿里云实际计费为准
- ASR 语音识别: ¥2.4/小时（实际使用流式服务）
- TTS 语音合成: ¥2/百万字符

---

## ⚙️ 配置步骤

### 方式 1: 使用 `.env` 文件（推荐）

1. 复制示例配置文件：
```bash
cp .env.example .env
```

2. 编辑 `.env` 文件，填入实际值：
```bash
# 默认聊天 Provider：第三方 OpenAI-compatible 中转
AI_DEFAULT_PROVIDER=third-party
THIRD_PARTY_BASE_URL=https://api-s.zwenooo.link/v1
THIRD_PARTY_API_KEY=your_gateway_key
THIRD_PARTY_MODEL=gpt-5.2
THIRD_PARTY_FALLBACK_MODEL=qwen-plus

# DashScope / Qwen：向量化 + 语音
AI_BAILIAN_API_KEY=sk-your-actual-key-here
AI_EMBEDDING_MODEL=text-embedding-v3
APP_VOICE_INTERVIEW_LLM_PROVIDER=dashscope
```

> 安全提醒：请不要把真实密钥写入 `README`、测试文件或任意会提交到 Git 的文档。
> 真实值仅放在本地 `.env` 或系统环境变量中。

3. 启动应用时会自动读取 `.env` 文件

### 方式 2: 使用环境变量

```bash
# Linux/Mac
export AI_DEFAULT_PROVIDER=third-party
export THIRD_PARTY_BASE_URL=https://api-s.zwenooo.link/v1
export THIRD_PARTY_API_KEY=your_gateway_key
export THIRD_PARTY_MODEL=gpt-5.2
export AI_BAILIAN_API_KEY=sk-your-key
export AI_EMBEDDING_MODEL=text-embedding-v3

# Windows PowerShell
$env:AI_DEFAULT_PROVIDER="third-party"
$env:THIRD_PARTY_BASE_URL="https://api-s.zwenooo.link/v1"
$env:THIRD_PARTY_API_KEY="your_gateway_key"
$env:THIRD_PARTY_MODEL="gpt5.2"
$env:AI_BAILIAN_API_KEY="sk-your-key"
$env:AI_EMBEDDING_MODEL="text-embedding-v3"
```

### 方式 3: 在 IDE 中配置

**IDEA**:
1. Run → Edit Configurations
2. 选择 Spring Boot 配置
3. Environment variables 中添加上述变量

**VS Code**:
1. 创建 `.vscode/launch.json`
2. 添加 env 配置

---

## 🔍 验证配置

启动应用后，检查日志：

```text
✅ 成功日志示例:
QwenAsrService initialized with model: qwen3-asr-flash-realtime
QwenTtsService initialized with model: qwen3-tts-flash-realtime, voice: Cherry
[LlmProviderRegistry] Building ChatModel - Provider: third-party, BaseUrl: https://api-s.zwenooo.link/v1, Model: gpt5.2

❌ 失败日志示例:
WebSocket failed: Expected HTTP 101 response but was '401 Unauthorized'
（说明 DashScope API Key 无效或未配置）
```

---

## 💡 成本优化建议

1. **主聊天走中转，语音/向量走 Qwen**：不要让一个 Provider 同时承担全部能力。
2. **限制并发**：通过限流与会话控制减少语音链路消耗。
3. **为不同场景配置不同模型**：主聊天与向量化无需强行统一到同一个模型。
4. **控制情景模拟与语音时长**：通过 `plannedDuration` 等参数控制成本。

---

## 🆘 常见问题

**Q: 必须同时配置第三方中转和 DashScope 吗？**

A: 如果你只做部分文本调试，可以只配第三方中转。但只要涉及知识库向量化或语音能力，就仍然需要 DashScope Key。

**Q: 为什么默认聊天不直接走 Qwen？**

A: 因为当前项目的定位是多 Provider 平台，默认聊天应该优先走自定义 OpenAI-compatible 中转，而不是把所有文本能力都绑定到 Qwen。

**Q: API 密钥会泄露吗？**

A: `.env` 文件已加入 `.gitignore`，不会提交到 Git。请妥善保管您的密钥。

**Q: OpenAI-compatible 一定完全兼容吗？**

A: 不一定。不同中转在 `/chat/completions`、流式事件结构、结构化输出和工具调用上可能有差异，建议逐项验证。

**Q: 一个 DashScope Key 真的够用吗？**

A: 对 DashScope 侧能力来说是够用的，Embedding、ASR、TTS 可以共用一个 Key；但默认聊天入口仍建议单独走第三方中转。

**Q: 为什么环境变量里还是 `VOICE_INTERVIEW`？**

A: 这是当前重构阶段保留的兼容命名，避免一次性破坏已有部署脚本。业务语义已经按“通用语音交互”理解。

---

## 📞 获取帮助

- DashScope 文档: https://help.aliyun.com/zh/dashscope/
- Qwen3 实时语音文档: https://help.aliyun.com/zh/model-studio/realtime-api-reference
- Spring AI OpenAI 兼容文档: https://docs.spring.io/spring-ai/reference/api/chat/openai-chat.html
