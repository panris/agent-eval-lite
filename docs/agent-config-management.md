# Agent 配置管理系统（页面配置 + 模板化）

## 系统架构

```
┌─────────────┐
│ 前端页面     │  配置 Agent
└──────┬──────┘
       │
       ▼
┌──────────────────┐
│ AgentConfig      │  持久化到 data/agents.json
│ Controller       │
└──────┬───────────┘
       │
       ▼
┌──────────────────┐
│ AgentFactory     │  根据 AgentConfig 创建 Agent 实例
└──────┬───────────┘
       │
       ▼
┌──────────────────┐
│ ConfigurableHTTP │  通过 JSONPath 映射调用实际 Agent
│ Agent            │
└──────────────────┘
```

---

## 核心功能

### 1. Agent 配置持久化

**数据模型**（`AgentConfig`）：
- 基本信息：name, type, description
- 连接配置：endpoint, headers, timeout
- 请求映射：template, inputField, staticFields
- 响应映射：outputPath, errorPath, errorMessagePath
- 类型配置：config（apiKey, model 等）

**存储位置**：`data/agents.json`

---

### 2. 预设模板

| 模板 | 类型 | 说明 |
|------|------|------|
| OpenAI GPT-4 | openai | OpenAI Chat Completion API |
| Anthropic Claude | claude | Claude Messages API |
| 自定义 HTTP | custom | 完全自定义 JSONPath 映射 |
| 简单 HTTP | http | 简单 input/output 格式 |

---

### 3. JSONPath 映射

**请求映射**：
```yaml
# 模板（带占位符）
template: '{"model":"${model}","messages":[{"role":"user","content":"${input}"}]}'

# 静态字段
staticFields:
  model: gpt-3.5-turbo
  temperature: 0.7

# 占位符会被替换：
# ${input} → 用户输入
# ${model} → config.model 的值
# ${apiKey} → config.apiKey 的值
```

**响应映射**：
```yaml
# 输出路径（JSONPath 或简单路径）
outputPath: "choices[0].message.content"

# 错误检测路径
errorPath: "error"

# 错误消息路径
errorMessagePath: "error.message"
```

---

## API 使用

### 1. 获取所有 Agent 配置

```bash
GET /api/agents

# 响应
{
  "success": true,
  "agents": [
    {
      "id": "agent-1",
      "name": "My GPT-4 Agent",
      "type": "openai",
      "endpoint": "https://api.openai.com/v1/chat/completions",
      "config": {
        "apiKey": "sk-xxx",
        "model": "gpt-4"
      },
      ...
    }
  ],
  "total": 1
}
```

### 2. 获取预设模板

```bash
GET /api/agents/templates

# 响应
{
  "success": true,
  "templates": [
    { "name": "OpenAI GPT-4", "type": "openai", ... },
    { "name": "Anthropic Claude", "type": "claude", ... },
    ...
  ]
}
```

### 3. 从模板创建配置

```bash
POST /api/agents/from-template/openai
Content-Type: application/json

{
  "name": "我的 GPT-4 Agent",
  "description": "用于问答评测",
  "config": {
    "apiKey": "sk-your-key-here",
    "model": "gpt-4"
  }
}

# 响应
{
  "success": true,
  "agent": {
    "id": "agent-uuid",
    "name": "我的 GPT-4 Agent",
    ...
  }
}
```

### 4. 创建自定义配置

```bash
POST /api/agents
Content-Type: application/json

{
  "name": "自定义 Agent",
  "type": "custom",
  "endpoint": "http://localhost:8000/api/chat",
  "headers": {
    "Content-Type": "application/json",
    "Authorization": "Bearer my-token"
  },
  "timeout": 30000,
  "requestMapping": {
    "template": "{\"query\":\"${input}\",\"context\":{\"user\":\"test\"}}",
    "inputField": "query",
    "staticFields": {
      "context": {"user": "test"}
    }
  },
  "responseMapping": {
    "outputPath": "data.answer",
    "errorPath": "error",
    "errorMessagePath": "error.message"
  },
  "config": {
    "customField": "value"
  }
}
```

### 5. 更新配置

```bash
PUT /api/agents/{id}
Content-Type: application/json

{
  "name": "更新后的名称",
  "config": {
    "model": "gpt-4-turbo"
  }
}
```

### 6. 删除配置

```bash
DELETE /api/agents/{id}
```

### 7. 测试配置

```bash
POST /api/agents/{id}/test
Content-Type: application/json

{
  "input": "测试输入"
}
```

---

## 使用示例

### 示例 1：配置 OpenAI GPT-4

```bash
# 1. 从模板创建
curl -X POST http://localhost:8080/api/agents/from-template/openai \
  -H "Content-Type: application/json" \
  -d '{
    "name": "GPT-4 问答 Agent",
    "config": {
      "apiKey": "sk-your-key",
      "model": "gpt-4"
    }
  }'

# 2. 获取 ID
# 响应中的 agent.id

# 3. 在评测时使用
curl -X POST http://localhost:8080/api/evaluate \
  -H "Content-Type: application/json" \
  -d '{
    "agentConfigId": "agent-id-from-step-2",
    "testCases": [
      {"input": "什么是AI？", "expected": "..."}
    ],
    "metrics": ["correctness"]
  }'
```

### 示例 2：配置自定义 Agent

假设你的 Agent API：
- 端点：`http://localhost:8000/api/chat`
- 请求格式：`{"query": "用户问题", "user_id": "test"}`
- 响应格式：`{"success": true, "data": {"answer": "回答内容"}}`

```bash
# 创建配置
curl -X POST http://localhost:8080/api/agents \
  -H "Content-Type: application/json" \
  -d '{
    "name": "本地 Agent",
    "type": "custom",
    "endpoint": "http://localhost:8000/api/chat",
    "headers": {
      "Content-Type": "application/json"
    },
    "requestMapping": {
      "template": "{\"query\":\"${input}\",\"user_id\":\"test\"}",
      "inputField": "query"
    },
    "responseMapping": {
      "outputPath": "data.answer",
      "errorPath": "success",
      "errorMessagePath": "error"
    }
  }'
```

### 示例 3：配置 Claude

```bash
curl -X POST http://localhost:8080/api/agents/from-template/claude \
  -H "Content-Type: application/json" \
  -d '{
    "name": "Claude Agent",
    "config": {
      "apiKey": "sk-ant-xxx",
      "model": "claude-3-sonnet-20240229"
    }
  }'
```

---

## 前端集成（待实现）

### Agent 配置管理页面

```html
<!-- agents.html -->
<div class="container">
  <h2>Agent 配置管理</h2>

  <!-- 模板选择 -->
  <div class="templates">
    <h3>从模板创建</h3>
    <button onclick="createFromTemplate('openai')">OpenAI GPT-4</button>
    <button onclick="createFromTemplate('claude')">Claude</button>
    <button onclick="createFromTemplate('custom')">自定义</button>
  </div>

  <!-- Agent 列表 -->
  <table id="agents-table">
    <thead>
      <tr>
        <th>名称</th>
        <th>类型</th>
        <th>端点</th>
        <th>操作</th>
      </tr>
    </thead>
    <tbody>
      <!-- 动态填充 -->
    </tbody>
  </table>

  <!-- 配置表单 -->
  <div id="agent-form" style="display:none;">
    <h3>配置 Agent</h3>
    <form>
      <input type="text" id="agent-name" placeholder="名称" required>
      <input type="text" id="agent-endpoint" placeholder="端点URL" required>
      <textarea id="agent-headers" placeholder="Headers (JSON)"></textarea>
      <textarea id="agent-request-template" placeholder="请求模板"></textarea>
      <input type="text" id="agent-output-path" placeholder="输出路径">
      <textarea id="agent-config" placeholder="配置 (JSON)"></textarea>
      <button type="submit">保存</button>
    </form>
  </div>
</div>
```

---

## JSONPath 语法参考

### 简单路径
```
output                    → {"output": "value"}
choices[0].message        → {"choices": [{"message": "value"}]}
data.items[0].text        → {"data": {"items": [{"text": "value"}]}}
```

### JSONPath 语法（以 $ 开头）
```
$.output                  → 根对象
$.choices[0].message      → 数组索引
$.data.items[*].text      → 所有数组元素
$.store.book[-1:]         → 最后一本书
$.store.book[0,1]         → 前两本书
```

---

## 下一步

### 前端页面开发（需要实现）
1. **Agent 列表页**：显示所有配置，支持 CRUD
2. **模板选择页**：一键从模板创建
3. **配置编辑页**：可视化表单编辑
4. **测试页**：实时测试配置是否正确

### 评测集成
在 `EvalController` 中添加 `agentConfigId` 参数：
```java
@PostMapping("/api/evaluate")
public Map<String, Object> evaluate(
    @RequestParam(required = false) String agentConfigId,
    @RequestBody EvalRequest request
) {
    AgentConfig config = agentConfigRepository.findById(agentConfigId).orElse(null);
    Agent agent = agentFactory.createAgent(config);
    // ...
}
```

---

## 已完成

- ✅ AgentConfig 数据模型
- ✅ AgentConfigRepository 持久化
- ✅ AgentTemplates 预设模板
- ✅ JsonPathUtils 映射工具
- ✅ ConfigurableHttpAgent 可配置 Agent
- ✅ AgentConfigController REST API
- ✅ AgentFactory 支持配置化创建

## 待完成

- 📋 前端 Agent 配置管理页面
- 📋 评测时选择已配置的 Agent
- 📋 Agent 配置测试功能
- 📋 Agent 配置导入/导出
