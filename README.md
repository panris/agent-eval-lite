# Agent Eval Lite

轻量级 Java Agent 评测系统 — 独立部署，开箱即用。

## 🚀 快速开始

### 方式一：直接运行（推荐）
```bash
# 1. 编译
mvn compile

# 2. 启动
mvn spring-boot:run

# 3. 访问
# 首页: http://localhost:8080
# 管理页: http://localhost:8080/manage
```

### 方式二：Docker 部署
```bash
# 1. 打包
mvn package -DskipTests

# 2. 构建镜像
docker build -t agent-eval-lite .

# 3. 运行
docker run -p 8080:8080 agent-eval-lite
```

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

## ⌨️ 键盘快捷键

| 快捷键 | 功能 |
|--------|------|
| Ctrl/Cmd + Enter | 提交表单 |
| Ctrl/Cmd + D | 切换深色模式 |
| Ctrl/Cmd + F | 聚焦搜索框 |
| Esc | 关闭模态框 |

## 🛠️ 技术栈

- **后端**: Java 17 + Spring Boot 3.2 + Thymeleaf
- **前端**: Vanilla JS + Chart.js + Flatpickr
- **存储**: JSON 文件（无需数据库）
- **构建**: Maven

## 📊 功能特性

- ✅ 测试用例 CRUD
- ✅ 分组管理
- ✅ 多指标评测（Correctness / Safety / ResponseTime）
- ✅ 评测历史趋势图
- ✅ 报告分享
- ✅ 深色模式
- ✅ 响应式设计
- ✅ 搜索防抖
- ✅ 自动数据清理

## 📝 开发计划

- [ ] 支持 Excel 导入/导出
- [ ] 添加更多评测指标
- [ ] 邮件通知
- [ ] 多用户支持
- [ ] 数据库持久化（可选）

## 📄 许可证

MIT License

---

**作者**: panris  
**GitHub**: [github.com/panris/agent-eval-lite](https://github.com/panris/agent-eval-lite)
