# 快速开始：连接你的 Agent 进行评测

## 步骤 1：准备测试用例

在管理页面创建测试用例：

| 输入 | 期望输出 | 分组 |
|------|---------|------|
| 什么是机器学习？ | 机器学习是人工智能的一个分支... | 基础知识 |
| Python 有哪些优点？ | Python 简洁易学、生态丰富... | 编程语言 |
| 如何优化 SQL 查询？ | 可以通过索引、查询重写... | 数据库 |

---

## 步骤 2：配置你的 Agent

### 方式 A：HTTP Agent（推荐）

如果你的 Agent 是一个 HTTP 服务（例如 Flask/FastAPI/Spring Boot）：

```yaml
# src/main/resources/application.yml
agent:
  default:
    type: http
  http:
    endpoint: "http://localhost:8000/api/chat"  # 你的 Agent 地址
    timeout: 30000
```

**你的 Agent 需要接收以下格式的请求**：
```json
POST /api/chat
{
  "input": "什么是机器学习？"
}
```

**并返回以下格式的响应**：
```json
{
  "output": "机器学习是人工智能的一个分支..."
}
```

---

### 方式 B：OpenAI Agent

如果你想评测 GPT-4 或其他 OpenAI 模型：

```bash
# 设置环境变量
export OPENAI_API_KEY="sk-your-api-key-here"
```

```yaml
# src/main/resources/application.yml
agent:
  default:
    type: openai
  openai:
    model: "gpt-4"  # 或 gpt-3.5-turbo
    system-prompt: "你是一个有帮助的AI助手。"
```

---

## 步骤 3：启动应用

```bash
# 启动应用
java -jar target/agent-eval-lite-1.0.0.jar

# 或使用 Maven
mvn spring-boot:run
```

访问：http://localhost:8080/manage

---

## 步骤 4：开始评测

### 方式 A：通过前端 UI

1. 访问 http://localhost:8080/manage
2. 选择要评测的测试用例（勾选复选框）
3. 点击"开始评测"按钮
4. 查看评测结果

### 方式 B：通过 API

```bash
# 评测选中的测试用例
curl -X POST http://localhost:8080/api/evaluate/by-case-ids \
  -H "Content-Type: application/json" \
  -d '{
    "agentType": "http",
    "caseIds": ["case-1", "case-2", "case-3"],
    "metrics": ["correctness", "safety", "response_time"]
  }'
```

---

## 步骤 5：查看评测报告

### 前端查看

访问"评测历史"标签页，查看所有评测报告：
- 通过率统计
- 平均分数
- 详细评分明细
- 响应时间分析

### API 查询

```bash
# 获取报告列表
curl http://localhost:8080/api/reports

# 获取单个报告详情
curl http://localhost:8080/api/reports/{reportId}

# 导出 PDF
curl http://localhost:8080/api/reports/{reportId}/pdf -o report.pdf

# 导出 CSV
curl http://localhost:8080/api/reports/{reportId}/csv -o report.csv
```

---

## 示例：评测本地 Python Agent

假设你有一个基于 FastAPI 的 Agent：

### 你的 Agent 代码（agent_server.py）

```python
from fastapi import FastAPI
from pydantic import BaseModel

app = FastAPI()

class ChatRequest(BaseModel):
    input: str

class ChatResponse(BaseModel):
    output: str

@app.post("/api/chat")
async def chat(request: ChatRequest):
    # 这里是你的 Agent 逻辑
    response = your_agent_model.predict(request.input)
    return ChatResponse(output=response)

if __name__ == "__main__":
    import uvicorn
    uvicorn.run(app, host="0.0.0.0", port=8000)
```

### 配置 Agent-Eval-Lite

```yaml
# application.yml
agent:
  default:
    type: http
  http:
    endpoint: "http://localhost:8000/api/chat"
    timeout: 30000
```

### 启动服务

```bash
# 终端 1：启动你的 Agent
python agent_server.py

# 终端 2：启动评测平台
mvn spring-boot:run
```

### 开始评测

```bash
# 创建测试用例
curl -X POST http://localhost:8080/api/testcases \
  -d '{
    "name": "基础问答",
    "input": "什么是深度学习？",
    "expected": "深度学习是机器学习的一个子集...",
    "group": "AI基础"
  }'

# 评测
curl -X POST http://localhost:8080/api/evaluate \
  -d '{
    "agentType": "http",
    "testCases": [
      {"input": "什么是深度学习？", "expected": "深度学习..."}
    ],
    "metrics": ["correctness", "safety"]
  }'
```

---

## 故障排查

### Agent 连接失败

```bash
# 检查 Agent 服务是否启动
curl http://localhost:8000/api/chat -d '{"input": "test"}'

# 检查日志
tail -f logs/application.log | grep "HttpAgent"
```

### 响应格式错误

确保你的 Agent 返回正确的 JSON 格式：
```json
{
  "output": "回答内容"
}
```

如果格式不同，可以自定义：

```bash
curl -X POST http://localhost:8080/api/evaluate \
  -d '{
    "agentType": "http",
    "agentConfig": {
      "endpoint": "http://localhost:8000/api/chat",
      "responseFormat": "simple"
    },
    "testCases": [...]
  }'
```

---

## 下一步

- ✅ 添加更多测试用例
- ✅ 配置多个分组（项目/模块/功能）
- ✅ 设置异步评测（大批量测试）
- ✅ 配置定时评测（CI/CD 集成）
- ✅ 导出评测报告（PDF/CSV/Excel）

---

## 需要帮助？

- 查看 [完整配置文档](./agent-configuration.md)
- 访问 [API 文档](http://localhost:8080/swagger-ui.html)
- 阅读 [评测指标说明](./docs/metrics.md)
