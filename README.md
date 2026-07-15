# Agent Eval Lite

轻量级 Java Agent 评测系统 — 独立部署，开箱即用。

---

## 📖 项目简介

Agent Eval Lite 是一个简单易用的 Java Agent 评测框架，支持测试用例管理、多指标评测、报告生成与导出，适合本地快速验证 Agent 输出质量。

---

## 🚀 快速开始

### 前置条件

- Java 21+（兼容 Java 17）
- Maven 3.6+

### 克隆与编译

```bash
git clone https://github.com/panris/agent-eval-lite.git
cd agent-eval-lite

# 编译测试
mvn clean test

# 打包
mvn package

# 运行
java -jar target/agent-eval-lite-*.jar

# 访问
# 首页: http://localhost:8080
# 管理页: http://localhost:8080/manage
```

### Docker 部署

```bash
# 打包并构建镜像
mvn package -DskipTests
docker build -t agent-eval-lite .

# 运行
docker run -p 8080:8080 agent-eval-lite
```

---

## 📊 功能列表

### 测试用例管理
- 测试用例 CRUD（创建、读取、更新、删除）
- 分组管理
- 三维维度标注（场景 / 难度 / 类型）
- 标签系统
- 批量操作

### 评测引擎
- 并发评测支持
- 多评分器：
  - `correctness` — 正确性评分
  - `safety` — 安全性评分
  - `response_time` — 响应时间
  - `bleu` — BLEU 分数
  - `rouge` — ROUGE 分数
  - `similarity` — 语义相似度

### 报告管理
- 评测报告查看、对比、分享
- 收藏与备注
- 导出格式：JSON / CSV / PDF（含中文支持）

### 异步评测
- 任务队列跟踪
- 状态轮询接口

### 需求文档生成测试用例
- Markdown 解析
- 编号列表解析
- 段落解析

---

## 🛠️ 技术栈

| 层级 | 技术 |
|------|------|
| 语言 | Java 17+ |
| 框架 | Spring Boot 3.x |
| 模板引擎 | Thymeleaf |
| 前端图表 | Chart.js |
| JSON 处理 | Jackson |
| PDF 生成 | OpenPDF |
| 构建工具 | Maven |
| 存储 | JSON 文件（无需数据库） |

---

## 📁 项目结构

```
src/main/java/io/github/panris/agenteval/
├── AgentEvalApplication.java      # 应用入口
├── Agent.java                    # Agent 模型
├── AgentOutput.java              # Agent 输出模型
├── Evaluation.java               # 评测请求/结果模型
├── EvaluationReport.java         # 评测报告模型
├── Evaluator.java                # 评测执行器
├── TestCase.java                 # 测试用例模型
├── config/                       # 配置类
├── model/                        # 数据模型
├── repository/                   # 数据持久化（JSON 文件存储）
├── scorer/                       # 各评分器实现（correctness/safety/bleu/rouge/similarity）
├── service/                      # 业务逻辑服务
└── web/                          # Web 控制器

src/main/resources/
├── application.properties        # Spring Boot 配置
└── templates/                    # Thymeleaf HTML 模板
```

---

## ⚙️ 配置

数据目录可通过系统属性配置（默认使用 `user.home/.agenteval`）：

```bash
java -Dagenteval.data.dir=/path/to/data -jar target/agent-eval-lite-*.jar
```

---

## 📚 API 文档

### 测试用例管理
- `GET /api/testcases` — 获取所有测试用例
- `POST /api/testcases` — 创建测试用例
- `PUT /api/testcases/{id}` — 更新测试用例
- `DELETE /api/testcases/{id}` — 删除测试用例

### 评测
- `POST /api/evaluate` — 运行评测
  ```json
  {
    "testCaseIds": ["id1", "id2"],
    "metrics": ["correctness", "safety"]
  }
  ```

### 报告
- `GET /api/reports` — 获取评测历史
- `GET /api/reports/{id}` — 获取报告详情
- `POST /api/reports/{id}/share` — 分享报告

---

## ⌨️ 键盘快捷键

| 快捷键 | 功能 |
|--------|------|
| Ctrl/Cmd + Enter | 提交表单 |
| Ctrl/Cmd + D | 切换深色模式 |
| Ctrl/Cmd + F | 聚焦搜索框 |
| Esc | 关闭模态框 |

---

## 🧩 功能特性

- ✅ 测试用例 CRUD
- ✅ 分组管理
- ✅ 多指标评测（Correctness / Safety / ResponseTime / BLEU / ROUGE / Similarity）
- ✅ 评测历史趋势图
- ✅ 报告分享
- ✅ 深色模式
- ✅ 响应式设计
- ✅ 搜索防抖
- ✅ 自动数据清理
- ✅ PDF / CSV / JSON 导出

---

## 📝 开发计划

- [ ] 支持 Excel 导入/导出
- [ ] 添加更多评测指标
- [ ] 邮件通知
- [ ] 多用户支持
- [ ] 数据库持久化（可选）

---

## 📄 License

MIT License

---

**作者**: panris  
**GitHub**: [github.com/panris/agent-eval-lite](https://github.com/panris/agent-eval-lite)
