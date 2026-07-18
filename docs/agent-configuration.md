# Agent 连接配置指南

## 概述

Agent-Eval-Lite 现在支持连接外部 Agent 进行评测。支持三种类型：
- **HTTP Agent**：调用任意 HTTP REST API
- **OpenAI Agent**：调用 OpenAI 兼容 API（GPT-4、GPT-3.5 等）
- **Demo Agent**：内置演示 Agent（默认）

---

## 快速开始

### 1. 配置 HTTP Agent

如果你的 Agent 是一个 HTTP 服务（例如监听在 `http://localhost:8000`），在 `application.yml` 中配置：

```yaml
agent:
  default:
    type: http
  http:
    endpoint: "http://localhost:8000/api/chat"
    timeout: 30000  # 30秒超时
```

#### 默认请求格式

Agent-Eval-Lite 会发送 POST 请求：
```json
{
  "input": "用户输入的测试问题"
}
```

期望响应格式：
```json
{
  "output": "Agent 的回答"
}
```

#### 自定义请求格式

如果你的 Agent 使用不同的格式，可以在评测时通过 API 参数指定：

```bash
curl -X POST http://localhost:8080/api/evaluate \
  -H "Content-Type: application/json" \
  -d '{
    "agentType": "http",
    "agentConfig": {
      "endpoint": "http://localhost:8000/api/chat",
      "requestFormat": "prompt",
      "responseFormat": "simple"
    },
    "testCases": [
      {"input": "什么是机器学习？", "expected": "机器学习是..."}
    ]
  }'
```

#### 支持的请求格式

| 格式 | 请求体示例 | 用途 |
|------|-----------|------|
| `simple` | `{"input": "..."}` | 默认格式 |
| `prompt` | `{"prompt": "..."}` | 提示词格式 |
| `query` | `{"query": "..."}` | 查询格式 |
| `openai` | `{"messages": [{"role": "user", "content": "..."}]}` | OpenAI 格式 |

#### 支持的响应格式

| 格式 | 响应体示例 | 用途 |
|------|-----------|------|
| `simple` | `{"output": "..."}` | 默认格式 |
| `openai` | `{"choices": [{"message": {"content": "..."}}]}` | OpenAI 格式 |
| `text` | `"纯文本"` | 纯文本响应 |

#### 自定义 HTTP Headers

```bash
curl -X POST http://localhost:8080/api/evaluate \
  -H "Content-Type: application/json" \
  -d '{
    "agentType": "http",
    "agentConfig": {
      "endpoint": "http://localhost:8000/api/chat",
      "headers": {
        "Authorization": "Bearer your-token",
        "X-Custom-Header": "value"
      }
    },
    "testCases": [...]
  }'
```

---

### 2. 配置 OpenAI Agent

如果你想评测 GPT-4 或 GPT-3.5，配置 OpenAI API：

#### 方法 A：通过配置文件

```yaml
agent:
  default:
    type: openai
  openai:
    api-key: "sk-your-api-key"  # 或使用环境变量
    model: "gpt-4"
    system-prompt: "你是一个有帮助的AI助手。"
```

#### 方法 B：通过环境变量（推荐）

```bash
export OPENAI_API_KEY="sk-your-api-key"
```

然后在 `application.yml` 中：
```yaml
agent:
  default:
    type: openai
  openai:
    api-key: ${OPENAI_API_KEY:}
    model: "gpt-4"
```

#### 方法 C：通过 API 参数

```bash
curl -X POST http://localhost:8080/api/evaluate \
  -H "Content-Type: application/json" \
  -d '{
    "agentType": "openai",
    "agentConfig": {
      "apiKey": "sk-your-api-key",
      "model": "gpt-4",
      "systemPrompt": "你是一个专业的技术顾问。"
    },
    "testCases": [
      {"input": "什么是微服务？", "expected": "微服务是..."}
    ]
  }'
```

#### 使用非官方 OpenAI 端点

如果你使用的是 OpenAI 兼容的服务（如 Azure OpenAI、国内代理等）：

```yaml
agent:
  openai:
    endpoint: "https://your-proxy.com/v1/chat/completions"
    api-key: "your-key"
    model: "gpt-4"
```

---

### 3. 使用 Demo Agent（默认）

Demo Agent 是一个简单的演示 Agent，支持：
- 加法运算（例如 `3 + 5` → `8`）
- 乘法运算（例如 `4 * 6` → `24`）
- Echo 回显（其他输入返回 `I'm a demo agent. You asked: ...`）

不需要配置，直接使用：

```yaml
agent:
  default:
    type: demo
```

---

## API 使用示例

### 单次评测

```bash
# 评测 HTTP Agent
curl -X POST http://localhost:8080/api/evaluate \
  -H "Content-Type: application/json" \
  -d '{
    "agentType": "http",
    "testCases": [
      {"input": "你好", "expected": "你好！有什么可以帮助你的？"}
    ],
    "metrics": ["correctness", "safety"]
  }'

# 评测 OpenAI Agent
curl -X POST http://localhost:8080/api/evaluate \
  -H "Content-Type: application/json" \
  -d '{
    "agentType": "openai",
    "agentConfig": {
      "apiKey": "sk-xxx",
      "model": "gpt-4"
    },
    "testCases": [
      {"input": "什么是AI？", "expected": "AI是人工智能..."}
    ],
    "metrics": ["correctness", "safety", "response_time"]
  }'
```

### 按测试用例 ID 评测

```bash
# 假设你已经创建了测试用例，ID为 "case-1", "case-2"
curl -X POST http://localhost:8080/api/evaluate/by-case-ids \
  -H "Content-Type: application/json" \
  -d '{
    "agentType": "http",
    "caseIds": ["case-1", "case-2"],
    "metrics": ["correctness", "safety"]
  }'
```

### 异步评测

```bash
# 提交异步评测任务
curl -X POST http://localhost:8080/api/evaluate/async \
  -H "Content-Type: application/json" \
  -d '{
    "agentType": "openai",
    "agentConfig": {
      "apiKey": "sk-xxx",
      "model": "gpt-4"
    },
    "testCases": [
      {"input": "问题1", "expected": "答案1"},
      {"input": "问题2", "expected": "答案2"}
    ],
    "metrics": ["correctness"]
  }'

# 响应
{
  "success": true,
  "taskId": "task-uuid-here"
}

# 查询任务状态
curl http://localhost:8080/api/evaluate/async/{taskId}

# 响应
{
  "taskId": "task-uuid-here",
  "status": "COMPLETED",
  "reportId": "report-uuid-here"
}
```

---

## 前端使用

在管理页面（`http://localhost:8080/manage`）：

1. **创建测试用例**：
   - 输入测试问题
   - 填写期望输出
   - 选择分组（可选）

2. **配置 Agent**：
   - 在"评测设置"中选择 Agent 类型
   - 如果选择 HTTP/OpenAI，填写对应配置

3. **开始评测**：
   - 点击"开始评测"按钮
   - 查看实时评测结果

---

## 高级配置

### 自定义请求/响应解析器

如果你的 Agent 使用复杂的请求/响应格式，可以实现自定义解析器：

```java
// 自定义请求格式
HttpAgent.RequestFormatter myFormatter = input -> {
    return Map.of(
        "query", input,
        "context", Map.of("user", "test-user"),
        "options", Map.of("temperature", 0.7)
    );
};

// 自定义响应解析器
HttpAgent.ResponseParser myParser = response -> {
    if (response instanceof Map) {
        Map<?, ?> map = (Map<?, ?>) response;
        Object data = map.get("data");
        if (data instanceof Map) {
            return ((Map<?, ?>) data).get("answer").toString();
        }
    }
    return "ERROR: Invalid response format";
};

// 创建 Agent
Agent agent = new HttpAgent(
    "http://localhost:8000/api/chat",
    Map.of("Authorization", "Bearer token"),
    myFormatter,
    myParser,
    30000
);
```

---

## 故障排查

### Agent 连接失败

**症状**：评测返回 `ERROR: Connection refused`

**解决**：
1. 检查 Agent 服务是否启动：`curl http://localhost:8000/health`
2. 检查防火墙是否阻止连接
3. 增加超时时间：`timeout: 60000`

### OpenAI API 调用失败

**症状**：评测返回 `ERROR: 401 Unauthorized`

**解决**：
1. 检查 API Key 是否正确
2. 检查是否有额度：访问 [OpenAI Dashboard](https://platform.openai.com/usage)
3. 检查模型名称：确保使用 `gpt-4` 或 `gpt-3.5-turbo`

### 响应格式错误

**症状**：评测返回 `ERROR: Failed to parse response`

**解决**：
1. 使用 `responseFormat` 参数指定正确格式
2. 检查 Agent 返回的 JSON 结构是否符合预期
3. 查看日志：`tail -f logs/application.log`

---

## 最佳实践

1. **安全**：
   - 不要在代码中硬编码 API Key
   - 使用环境变量或配置文件
   - 生产环境使用密钥管理服务

2. **性能**：
   - 合理设置超时时间（建议 30-60 秒）
   - 使用异步评测处理大批量测试
   - 控制并发数（默认 4 线程）

3. **可观测性**：
   - 开启 DEBUG 日志查看详细请求/响应
   - 监控评测成功率和平均耗时
   - 记录 Agent 调用失败的原因

4. **成本控制**：
   - OpenAI API 按 Token 计费，控制测试用例数量
   - 使用 `gpt-3.5-turbo` 进行大量测试
   - 使用 `gpt-4` 进行关键测试

---

## 示例场景

### 场景 1：评测本地 Agent 服务

```yaml
# application.yml
agent:
  default:
    type: http
  http:
    endpoint: "http://localhost:8000/chat"
    timeout: 30000
```

```bash
# 启动评测
curl -X POST http://localhost:8080/api/evaluate/by-case-ids \
  -d '{"caseIds": ["case-1", "case-2"], "metrics": ["correctness"]}'
```

### 场景 2：评测 GPT-4

```bash
# 设置环境变量
export OPENAI_API_KEY="sk-xxx"

# 启动应用
java -jar agent-eval-lite.jar

# 评测
curl -X POST http://localhost:8080/api/evaluate \
  -d '{
    "agentType": "openai",
    "agentConfig": {"model": "gpt-4"},
    "testCases": [...]
  }'
```

### 场景 3：评测多个 Agent 对比

```bash
# 评测 Agent A
curl -X POST http://localhost:8080/api/evaluate \
  -d '{"agentType": "http", "agentConfig": {"endpoint": "http://agent-a/api"}, "testCases": [...]}' \
  | jq '.reportId' > report-a.txt

# 评测 Agent B
curl -X POST http://localhost:8080/api/evaluate \
  -d '{"agentType": "http", "agentConfig": {"endpoint": "http://agent-b/api"}, "testCases": [...]}' \
  | jq '.reportId' > report-b.txt

# 对比报告
curl -X POST http://localhost:8080/api/reports/compare \
  -d '{"reportIds": ["report-a-id", "report-b-id"]}'
```

---

## 下一步

- 查看 [API 文档](http://localhost:8080/swagger-ui.html)
- 阅读 [评测指标说明](./docs/metrics.md)
- 了解 [如何编写优质测试用例](./docs/test-cases-best-practices.md)
