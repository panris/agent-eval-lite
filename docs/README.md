# Agent Eval Lite — 系统说明文档

> **项目地址**: [github.com/panris/agent-eval-lite](https://github.com/panris/agent-eval-lite)  
> **版本**: 0.1.0 | **Java**: 17 | **Spring Boot**: 3.2.2  
> **最后更新**: 2026-07-11

---

## 目录

1. [系统概述](#1-系统概述)
2. [技术架构](#2-技术架构)
3. [核心概念](#3-核心概念)
4. [快速开始](#4-快速开始)
5. [系统架构详解](#5-系统架构详解)
6. [API 参考](#6-api-参考)
7. [评测指标体系](#7-评测指标体系)
8. [前端功能说明](#8-前端功能说明)
9. [数据存储](#9-数据存储)
10. [运维指南](#10-运维指南)
11. [安全考量](#11-安全考量)
12. [开发指南](#12-开发指南)

---

## 1. 系统概述

Agent Eval Lite 是一个**轻量级 Java Agent 评测框架**，用于评估 AI Agent 在给定测试用例上的表现。

### 1.1 设计目标

- **零配置**：无需数据库，启动即用
- **可扩展**：通过 `@scorer` 注解或接口扩展评测指标
- **多维度**：支持按项目 / 模块 / 功能 / 用例说明四维度组织测试用例
- **异步批量**：大批量评测任务异步执行，支持任务状态跟踪和超时中断
- **报告管理**：生成、查看、对比、分享、收藏、导出（JSON / CSV / Excel / PDF）

### 1.2 系统角色

| 角色 | 说明 |
|------|------|
| **测试用例 (TestCase)** | 输入 + 期望输出，附四维分组和说明 |
| **评测指标 (Scorer)** | 独立评分器，如正确性、安全性、响应时间、BLEU、ROUGE、相似度 |
| **评测报告 (Report)** | 一次评测的完整结果，含所有用例评分和统计 |
| **Agent** | 被评测的 AI 程序，由 `Agent` 函数式接口定义 |

---

## 2. 技术架构

### 2.1 技术栈

| 层级 | 技术选型 | 说明 |
|------|----------|------|
| **语言** | Java 17 | 最低要求 |
| **框架** | Spring Boot 3.2.2 | Web + Thymeleaf + Scheduling |
| **模板** | Thymeleaf | 服务端 HTML 渲染（index / manage / share） |
| **前端** | Vanilla JS + Chart.js + Flatpickr | 无框架依赖，渐进增强 |
| **JSON** | Jackson 2.16.1 | API 响应序列化 |
| **Excel** | Apache POI 5.2.5 | Excel 导入/导出 |
| **PDF** | OpenPDF 1.3.30 | 中文 PDF 生成（Noto CJK / WenQuanYi 字体） |
| **构建** | Maven 3 | 打包与依赖管理 |
| **容器** | Docker + docker-compose | 一键部署 |

### 2.2 模块结构

```
agent-eval-lite/
├── src/main/java/io/github/panris/agenteval/
│   ├── Agent.java              # Agent 函数式接口
│   ├── AgentOutput.java        # Agent 执行结果封装
│   ├── Evaluation.java          # 单用例评测结果
│   ├── EvaluationReport.java    # 报告（多用例汇总）
│   ├── Evaluator.java           # 评测引擎（Builder 模式）
│   ├── TestCase.java            # 纯数据对象
│   │
│   ├── model/
│   │   ├── TestCaseEntity.java  # 持久化实体（4维 + tags）
│   │   └── TestCaseGroup.java    # 分组实体
│   │
│   ├── repository/
│   │   └── TestCaseRepository.java  # JSON 文件持久化仓库
│   │
│   ├── scorer/
│   │   ├── EvaluationScorer.java   # 评分器接口
│   │   ├── ScorerResult.java       # 评分结果（分数/通过/理由/元信息）
│   │   └── builtin/
│   │       ├── CorrectnessScorer.java   # 正确性评分（Jaccard 词级相似度）
│   │       ├── SafetyScorer.java         # 安全性检查（正则黑名单）
│   │       ├── ResponseTimeScorer.java   # 响应时间评分
│   │       ├── BleuScorer.java          # BLEU-4 词级精确率
│   │       ├── RougeScorer.java         # ROUGE-L（最长公共子序列）
│   │       └── SimilarityScorer.java    # Jaccard Trigram 相似度
│   │
│   ├── service/
│   │   ├── ReportService.java         # 报告 CRUD/对比/分享/持久化
│   │   ├── AsyncEvalService.java       # 异步评测任务队列（超时中断）
│   │   └── ReportCleanupService.java   # 定时清理（启动 + 每日 3 点）
│   │
│   ├── config/
│   │   └── AppConfig.java              # 线程池 Bean（@Bean evalTaskExecutor）
│   │
│   └── web/controller/
│       ├── EvalController.java         # 评测 + 报告管理 API
│       ├── TestCaseController.java     # 测试用例 CRUD API
│       ├── GroupController.java         # 分组管理 API
│       ├── ExcelController.java         # Excel/CSV 导入导出
│       ├── PdfController.java          # PDF 导出
│       ├── ShareController.java        # 分享链接（shareId → reportId）
│       └── HealthController.java        # 健康检查
│
├── src/main/resources/
│   ├── application.yml                 # Spring Boot 配置
│   └── templates/
│       ├── index.html                  # 首页（快速评测）
│       ├── manage.html                 # 管理后台（主入口）
│       └── share.html                  # 分享页（只读）
│
├── data/                              # JSON 数据文件（无需手动创建）
│   ├── testcases.json                  # 测试用例持久化
│   ├── groups.json                     # 分组持久化
│   ├── reports.json                    # 报告历史
│   └── shares.json                     # 分享链接映射（不提交 Git）
│
├── docker-compose.yml                  # Docker 编排
├── Dockerfile                          # 多阶段构建镜像
├── pom.xml                            # Maven 依赖配置
└── .dockerignore                      # Docker 构建排除
```

---

## 3. 核心概念

### 3.1 评测引擎 Evaluator

评测引擎是系统核心，通过 **Builder 模式** 构建，支持三种抽象级别：

```java
// 级别 1：零配置字符串（仅指定指标名称）
Evaluator evaluator = Evaluator.builder()
    .metrics("correctness", "safety")
    .build();

// 级别 2：@scorer 注解（传入自定义 Scorer 实例）
Evaluator evaluator = Evaluator.builder()
    .scorer(new MyCustomScorer())
    .metrics("correctness")
    .build();

// 级别 3：对象化配置（并发数 + 超时）
Evaluator evaluator = Evaluator.builder()
    .metrics("correctness", "bleu", "rouge")
    .maxWorkers(8)
    .timeoutMs(60000)
    .build();
```

执行评测返回 `EvaluationReport`，含统计摘要和逐用例结果。

### 3.2 Agent 接口

```java
@FunctionalInterface
public interface Agent {
    String execute(String input);
}
```

内置 Agent 类型（通过 `agentType` 参数）：

| 类型 | 行为 |
|------|------|
| `echo` | 直接返回输入 |
| `upper` | 返回大写输入 |
| `reverse` | 返回输入的反转字符串 |
| `demo`（默认） | 解析加减乘除数学表达式，其余返回"I'm a demo agent..." |

### 3.3 评分器 Scorer

每个评分器实现 `EvaluationScorer` 接口：

```java
public interface EvaluationScorer {
    String getName();                       // 指标名称，如 "correctness"
    ScorerResult evaluate(TestCase, AgentOutput);  // 核心评分逻辑
    default double getThreshold() { return 0.7; } // 默认通过阈值
}
```

评分结果 `ScorerResult` 包含：

| 字段 | 类型 | 说明 |
|------|------|------|
| `score` | double | 0.0 ~ 1.0 |
| `passed` | boolean | 是否通过（由阈值决定） |
| `rationale` | String | 人类可读评分说明 |
| `metadata` | Map | 额外信息（如 responseTimeMs） |

### 3.4 四维分组模型

测试用例的四个维度：

```
TestCaseEntity
├── groupId    : 分组（按功能划分的一组用例）
├── project    : 项目（维度一）
├── module     : 模块（维度二）
└── function   : 功能（维度三）
└── description: 用例说明（补充描述，不参与分组过滤）
```

分组 + 三维 + 说明构成完整的测试用例组织结构。评测时可选：
- 按 `groupId` 评测
- 按 project / module / function 任一维度筛选评测
- 报告历史支持按全部四个字段过滤

---

## 4. 快速开始

### 4.1 方式一：直接运行

```bash
# 1. 编译
mvn compile

# 2. 启动（默认端口 8080）
mvn spring-boot:run

# 3. 访问
open http://localhost:8080/manage
```

### 4.2 方式二：Docker 部署

```bash
# 1. 打包
mvn package -DskipTests

# 2. 构建并启动
docker compose up -d

# 3. 访问
open http://localhost:8080/manage
```

> Docker 镜像内已预装中文字体（Noto CJK + WenQuanYi），PDF 中文渲染开箱即用。

### 4.3 首次验证

```bash
curl http://localhost:8080/api/health | python3 -m json.tool
```

预期输出：

```json
{
  "status": "UP",
  "service": "agent-eval-lite",
  "version": "0.1.0",
  "testCases": 0,
  "reports": 0
}
```

---

## 5. 系统架构详解

### 5.1 数据流

```
HTTP Request
    ↓
Spring MVC Controller（参数校验）
    ↓
Service Layer（业务逻辑）
    ↓
Repository（JSON 文件持久化）
    ↓
JSON Files（data/*.json）
```

### 5.2 异步评测流程

```
POST /api/evaluate/async
    ↓
AsyncEvalService.submitTask()
    ├─ 生成 taskId，加入 ConcurrentHashMap
    ├─ 返回 { taskId, status: "PENDING" }
    └─ 提交到 ThreadPoolTaskExecutor（evalTaskExecutor）
            ↓
        @Scheduled(fixedRate = 5000) 每 5 秒扫描
            ├─ 检测 RUNNING 任务的 elapsedMs > timeoutSeconds
            ├─ 超时 → future.cancel(true) 发送线程中断信号
            └─ 状态 → "TIMED_OUT"
            ↓
        评测完成 → reportId → ReportService → reports.json
            ↓
GET /api/tasks/{taskId} → { status, reportId }
```

### 5.3 报告分享流程

```
POST /api/reports/{id}/share
    ↓
ReportService.createShareLink()
    ├─ 生成 shareId（UUID 前 8 位）
    ├─ sharedReports.put(shareId, reportId)
    └─ shares.json 持久化
    ↓
返回 /share/{shareId}
    ↓
GET /share/{shareId}
    ↓
ShareController → ReportService.resolveShareId()
    ├─ 找到 reportId → 302 重定向到 /manage
    └─ 找不到 → 404
```

> `sharedReports` 通过 Spring `@Bean` 注入在 `EvalController` 和 `ShareController` 间共享实例。

### 5.4 线程池配置（AppConfig）

```java
@Bean(name = "evalTaskExecutor")
ThreadPoolTaskExecutor evalTaskExecutor() {
    corePoolSize    = CPU核心数
    maxPoolSize     = CPU核心数 × 2
    queueCapacity   = 100
    拒绝策略         = CallerRunsPolicy（调用方线程执行）
    关闭时           = 等待任务完成（awaitTermination 30s）
}
```

---

## 6. API 参考

> 所有 API 路径前缀 `/api`，返回格式均为 JSON。

### 6.1 测试用例

| 方法 | 路径 | 说明 |
|------|------|------|
| `GET` | `/testcases` | 分页获取用例列表（支持 keyword/三维过滤） |
| `POST` | `/testcases` | 创建用例 |
| `GET` | `/testcases/{id}` | 获取单个用例 |
| `PUT` | `/testcases/{id}` | 更新用例 |
| `DELETE` | `/testcases/{id}` | 删除用例 |
| `DELETE` | `/testcases` | 批量删除（body: `{ "ids": [...] }`） |
| `POST` | `/testcases/batch` | 批量创建 |

**创建请求体示例：**

```json
{
  "name": "加法测试",
  "input": "2+2=?",
  "expected": "4",
  "groupId": "group-uuid",
  "project": "math",
  "module": "arithmetic",
  "function": "addition",
  "description": "验证整数加法计算能力"
}
```

**列表查询参数：**

| 参数 | 类型 | 说明 |
|------|------|------|
| `page` | int | 页码（默认 1） |
| `size` | int | 每页数量（默认 20，最大 100） |
| `keyword` | string | 搜索 name / input / expected / description |
| `groupId` | string | 按分组过滤 |
| `project` | string | 按项目维度过滤 |
| `module` | string | 按模块维度过滤 |
| `function` | string | 按功能维度过滤 |

### 6.2 分组管理

| 方法 | 路径 | 说明 |
|------|------|------|
| `GET` | `/groups` | 列表所有分组 |
| `POST` | `/groups` | 创建分组 |
| `GET` | `/groups/{id}` | 获取分组（含所属用例列表） |
| `PUT` | `/groups/{id}` | 更新分组 |
| `DELETE` | `/groups/{id}` | 删除分组 |
| `POST` | `/groups/{id}/testcases` | 添加用例到分组 |
| `DELETE` | `/groups/{id}/testcases/{testCaseId}` | 从分组移除用例 |

### 6.3 评测

| 方法 | 路径 | 说明 |
|------|------|------|
| `POST` | `/evaluate` | 同步评测（直接传入用例） |
| `POST` | `/evaluate/cases` | 同步评测（按用例 ID） |
| `POST` | `/evaluate/group/{groupId}` | 同步评测（按分组） |
| `POST` | `/evaluate/dimensions` | 同步评测（按三维维度） |
| `POST` | `/evaluate/async` | 异步批量评测 |
| `GET` | `/tasks/{taskId}` | 查询异步任务状态 |
| `GET` | `/tasks` | 列表最近 50 个异步任务 |

**同步评测请求体（`/evaluate`）：**

```json
{
  "agentType": "demo",
  "metrics": ["correctness", "safety", "response_time"],
  "testCases": [
    { "input": "2+2=?", "expected": "4" },
    { "input": "3*3=?", "expected": "9" }
  ]
}
```

**异步评测请求体（`/evaluate/async`）：**

```json
{
  "agentType": "demo",
  "metrics": ["correctness", "bleu"],
  "group": "数学评测",
  "project": "math",
  "module": "arithmetic",
  "function": "addition"
}
```

> 异步请求未传 `testCases` 时，自动按三维维度筛选全部匹配用例。

**任务状态响应：**

```json
{
  "success": true,
  "taskId": "task_1752312345678",
  "status": "RUNNING",
  "reportId": "",
  "totalCases": 20,
  "completedCases": 7,
  "timeoutSeconds": 300
}
```

状态枚举：`PENDING` → `RUNNING` → `COMPLETED` | `FAILED` | `TIMED_OUT`

### 6.4 报告管理

| 方法 | 路径 | 说明 |
|------|------|------|
| `GET` | `/reports` | 列表报告（分页 + 多维过滤） |
| `GET` | `/reports/{id}` | 报告详情 |
| `DELETE` | `/reports/{id}` | 删除报告 |
| `POST` | `/reports/{id}/copy` | 复制报告 |
| `POST` | `/reports/{id}/favorite` | 收藏/取消收藏 |
| `POST` | `/reports/{id}/share` | 生成分享链接 |
| `PUT` | `/reports/{id}/tags` | 更新标签 |
| `PUT` | `/reports/{id}/note` | 更新备注 |
| `GET` | `/reports/compare?ids=id1,id2` | 对比报告 |
| `GET` | `/reports/favorites` | 收藏列表 |
| `GET` | `/reports/{id}/export?format=csv|json` | 导出报告 |

**列表查询参数：**

| 参数 | 默认值 | 说明 |
|------|--------|------|
| `sort` | `desc` | 排序方向（asc / desc，按 timestamp） |
| `sortBy` | `time` | 排序字段（time / score） |
| `page` | `1` | 页码 |
| `size` | `20` | 每页数量（最大 100） |
| `all` | `false` | `true` 时返回全量（趋势图用，不分页） |
| `since` | — | 时间范围下限（Unix ms） |
| `until` | — | 时间范围上限（Unix ms） |
| `group` | — | 按分组过滤 |
| `project` | — | 按项目维度过滤 |
| `module` | — | 按模块维度过滤 |
| `function` | — | 按功能维度过滤 |
| `favorite` | — | `true` 收藏 / `false` 未收藏 |
| `status` | — | `passed`（通过率≥70%） / `failed` |
| `keyword` | — | 搜索 id / note / tags |

### 6.5 Excel / CSV

| 方法 | 路径 | 说明 |
|------|------|------|
| `GET` | `/testcases/export/excel` | 导出 Excel（含 10 列） |
| `GET` | `/testcases/export/csv` | 导出 CSV（含说明列） |
| `POST` | `/testcases/import/excel` | 导入 Excel |
| `POST` | `/testcases/import/csv` | 导入 CSV |

**Excel/CSV 导出列顺序（10 列）：**

| # | 列名 | 说明 |
|---|------|------|
| 1 | ID | UUID |
| 2 | 名称 | name |
| 3 | 输入 | input |
| 4 | 期望输出 | expected |
| 5 | 分组 | groupId |
| 6 | 项目 | project |
| 7 | 模块 | module |
| 8 | 功能 | function |
| 9 | 用例说明 | description |
| 10 | 创建时间 | createdAt |

**导入限制：**

- 文件大小 ≤ 10 MB
- 行数 ≤ 1000
- 单字段 ≤ 10000 字符

### 6.6 PDF

| 方法 | 路径 | 说明 |
|------|------|------|
| `GET` | `/reports/{id}/export/pdf` | 导出中文 PDF |

PDF 内容：标题、报告 ID、生成时间、统计卡片（用例数/通过/通过率/平均分）、执行耗时、分组维度（group / project / module / function）、逐用例结果表（名称/状态/评分/逐评分器明细）。

### 6.7 健康检查

| 方法 | 路径 | 说明 |
|------|------|------|
| `GET` | `/api/health` | 服务健康状态 |

```json
{
  "status": "UP",
  "service": "agent-eval-lite",
  "version": "0.1.0",
  "uptimeSeconds": 3600,
  "testCases": 68,
  "reports": 142
}
```

---

## 7. 评测指标体系

| 指标 | 类名 | 阈值 | 算法 | 说明 |
|------|------|------|------|------|
| `correctness` | `CorrectnessScorer` | 0.7 | Jaccard 词级相似度 | 核心正确性评分 |
| `safety` | `SafetyScorer` | 1.0 | 正则黑名单 | 含暴力/黑客/密码/违法关键词则失败 |
| `response_time` | `ResponseTimeScorer` | 0.7 | 线性衰减 | ≤1s满分，≤5s 0.7分，>5s 失败 |
| `bleu` | `BleuScorer` | 0.5 | 词级精确率 | n-gram 重叠率 |
| `rouge` | `RougeScorer` | 0.4 | LCS 比率 | 最长公共子序列与较长字符串之比 |
| `similarity` | `SimilarityScorer` | 0.5 | Jaccard Trigram | 字符三元组集合相似度 |

### 7.1 添加自定义评分器

步骤 1：实现 `EvaluationScorer` 接口

```java
package io.github.panris.agenteval.scorer.builtin;

import io.github.panris.agenteval.*;
import io.github.panris.agenteval.scorer.*;

public class MyCustomScorer implements EvaluationScorer {

    @Override
    public String getName() {
        return "my_custom_metric";
    }

    @Override
    public double getThreshold() {
        return 0.6;
    }

    @Override
    public ScorerResult evaluate(TestCase testCase, AgentOutput output) {
        if (output.hasError()) {
            return ScorerResult.failed("Agent 执行失败: " + output.getError().getMessage());
        }
        String expected = testCase.getExpectedOutput();
        String actual = output.getOutput();

        // 自定义评分逻辑
        double score = calculateScore(expected, actual);
        boolean passed = score >= getThreshold();

        return ScorerResult.of(score, passed,
            String.format("自定义指标: %.2f（阈值 %.2f）", score, getThreshold()));
    }

    private double calculateScore(String expected, String actual) {
        // ... 计算逻辑
        return 0.0;
    }
}
```

步骤 2：在 `Evaluator.Builder.createBuiltinScorer()` 中注册

```java
// Evaluator.java
private EvaluationScorer createBuiltinScorer(String name) {
    return switch (name.toLowerCase()) {
        case "correctness" -> new CorrectnessScorer();
        case "safety" -> new SafetyScorer();
        // ... 其他
        case "my_custom_metric" -> new MyCustomScorer();  // 添加这行
        default -> null;
    };
}
```

步骤 3：前端 `manage.html` 指标数组追加新值

```javascript
const ALL_METRICS = ['correctness', 'safety', 'response_time', 'bleu', 'rouge', 'similarity', 'my_custom_metric'];
```

---

## 8. 前端功能说明

### 8.1 页面结构

| 页面 | 路径 | 说明 |
|------|------|------|
| 首页 | `/` | 快速输入用例 + 选择指标，即时评测 |
| 管理后台 | `/manage` | 主入口：用例管理 / 评测控制 / 报告历史 |
| 分享页 | `/share/{shareId}` | 只读报告查看，UUID 前 8 位短链 |

### 8.2 管理后台功能模块

#### 8.2.1 测试用例管理

- **表格展示**：ID / 名称 / 输入 / 期望 / 分组 / 项目 / 模块 / 功能 / 说明 / 创建时间
- **搜索过滤**：keyword 同时搜索 name / input / expected / description
- **三维下拉**：project / module / function 下拉筛选（AND 关系）
- **分组筛选**：按分组下拉过滤
- **分页**：每页 20 条
- **批量选择**：Checkbox 批量选中，支持 Shift 多选
- **批量删除**：选中后一键删除（带确认）
- **批量编辑**：Modal 模态框，支持批量修改 name / expected / group / tags / project / module / function / description（字段级增删替换）
- **创建用例**：Modal 模态框，含全部四维字段

#### 8.2.2 评测控制

- **单条评测**：选中一行 → "评测" 按钮
- **选中评测**：选中多条 → "评测选中" 按钮
- **分组评测**：按分组一键评测全部分组用例
- **维度评测**：按三维维度筛选后评测
- **异步批量**：大批量评测异步提交，轮询任务状态
- **指标选择**：6 个指标复选框（正确性 / 安全性 / 响应时间 / BLEU / ROUGE / 相似度）

#### 8.2.3 报告历史

- **表格展示**：ID / 分组 / 项目 / 模块 / 功能 / 通过率 / 平均分 / 耗时 / 时间
- **多维过滤**：分组 / 三维下拉 + 日期范围 + 收藏 + 通过/未通过 + 关键字
- **4 态排序**：最新 / 最早 / 最高分 / 最低分
- **详情弹窗**：评测结果饼图 + 柱状图 + 逐用例表格
- **操作菜单**：查看 / 复制 / 收藏 / 分享 / 导出（JSON / CSV / PDF）
- **趋势图**：双 Y 轴折线图（通过率 + 平均分，全量无分页）
- **报告对比**：选中多条 → 对比弹窗（逐评分器明细 + 统计卡片）

#### 8.2.4 导入导出

- **Excel 导入**：拖拽或点击上传 `.xlsx`，实时预览结果（成功/跳过/错误）
- **Excel 导出**：下载含 10 列的 `.xlsx` 文件
- **CSV 导入/导出**：UTF-8 编码，含 description 列
- **JSON 导出**：单报告导出

### 8.3 键盘快捷键

| 快捷键 | 功能 |
|--------|------|
| `Ctrl/Cmd + Enter` | 提交表单 |
| `Ctrl/Cmd + D` | 切换深色模式 |
| `Ctrl/Cmd + F` | 聚焦搜索框 |
| `Esc` | 关闭模态框 |

### 8.4 深色模式

自动检测系统偏好（`prefers-color-scheme`），并通过 `localStorage('theme')` 持久化用户选择。

---

## 9. 数据存储

### 9.1 文件结构

```
data/
├── testcases.json      # 测试用例列表（JSON 数组）
├── groups.json         # 分组列表（JSON 数组）
├── reports.json        # 报告历史（JSON 对象，key = reportId）
└── shares.json         # 分享映射（JSON 对象，key = shareId，value = reportId）
```

> `shares.json` 不提交 Git（已加入 `.gitignore`），防止分享链接意外泄露。

### 9.2 持久化策略

| 文件 | 写入时机 | 读取时机 |
|------|----------|----------|
| `testcases.json` | 创建/更新/删除用例时 | 服务启动时 |
| `groups.json` | 同上 | 同上 |
| `reports.json` | 报告生成/更新/删除时 | 服务启动时 |
| `shares.json` | 分享创建时 | 服务启动时 |

### 9.3 报告自动清理

| 策略 | 触发条件 |
|------|----------|
| 启动时保守清理 | 超过 500 条时，按时间倒序淘汰超出的报告 |
| 每日凌晨 3 点完整清理 | 超过 500 条 或 超过 30 天时，触发清理 |

---

## 10. 运维指南

### 10.1 启动检查清单

```bash
# 1. 检查端口占用
lsof -i :8080

# 2. 如有旧进程，终止
kill -9 <PID>

# 3. 启动服务
cd /path/to/agent-eval-lite
mvn spring-boot:run

# 4. 验证
curl -s http://localhost:8080/api/health
```

### 10.2 Docker 运维

```bash
# 查看日志
docker compose logs -f app

# 重启
docker compose restart app

# 更新部署
git pull && docker compose up -d --build app

# 数据备份（data 目录已挂载宿主机 ./data，备份即备份该目录）
cp -r data data_backup_$(date +%Y%m%d)
```

### 10.3 常见问题

| 问题 | 原因 | 解决方案 |
|------|------|----------|
| 8080 端口占用 | 旧 Java 进程未退出 | `pkill -f spring-boot` 或 `kill -9 <PID>` |
| PDF 中文显示方块 | Docker 镜像未安装中文字体 | 使用官方 `docker-compose.yml`（已含字体） |
| 管理页空白 | 旧浏览器缓存 | `Cmd+Shift+R` 硬刷新 |
| 报告分享 404 | 服务重启后 shareId 丢失 | `shares.json` 未挂载持久化卷 |
| GitHub 贡献图无数据 | 系统时间设为未来 | 开启 macOS 自动设置时间 |

---

## 11. 安全考量

### 11.1 已实施的安全措施

| 措施 | 实现位置 | 说明 |
|------|----------|------|
| **XSS 防护** | 前端 `utils.js` — `escapeHtml()` | 所有 `innerHTML` 插值经转义 |
| **CSV 公式注入防护** | `EvalController.escapeCsv()` | 前缀 `= + - @` 转为 `'=` |
| **Excel 公式注入防护** | `ExcelController.sanitizeExcelCell()` | 同上，公式值取缓存结果 |
| **API 参数校验** | 各 Controller | null 检查、长度限制（≤100 条）、metrics 白名单 |
| **Thymeleaf 模板缓存** | `application.yml` — `spring.thymeleaf.cache: false` | 禁用缓存避免模板残留 |
| **上传文件限制** | `ExcelController` | 10 MB / 1000 行 / 10000 字符每字段 |

### 11.2 注意事项

- 本系统设计为**内网/开发环境**使用，未集成认证鉴权
- 如需公网暴露，建议在反向代理层（Nginx）配置 Basic Auth 或 JWT
- `shares.json` 包含报告 ID 映射，需妥善保管

---

## 12. 开发指南

### 12.1 项目导入 IDE

```bash
# IntelliJ IDEA
File → Open → 选择项目根目录 → Open as Project
# 自动识别 Maven 项目，下载依赖

# VS Code
code .
# 推荐安装 Extension Pack for Java (Microsoft)
```

### 12.2 运行测试

```bash
mvn test
```

当前测试覆盖：核心评测逻辑（Evaluator）、并发执行、报告生成。

### 12.3 添加新功能 Checklist

```
[ ] 后端：Controller 接收参数并校验
[ ] 后端：Service 层业务逻辑
[ ] 后端：Repository（如需持久化）
[ ] 前端：manage.html UI 交互
[ ] 前端：utils.js 通用工具（如需）
[ ] 后端：单元测试
[ ] 编译验证：mvn compile -q
[ ] 功能验证：mvn spring-boot:run 后手动测试
[ ] 推送：git add → git commit → git push
```

### 12.4 热重载

使用 Spring Boot DevTools（已在 `pom.xml` 中由 parent 引入）：

```bash
mvn spring-boot:run
# 修改 Java 文件后自动重启
# 修改 resources 文件后自动重载
```

### 12.5 配置说明

`src/main/resources/application.yml`：

```yaml
server:
  port: 8080

spring:
  thymeleaf:
    cache: false   # 生产环境改为 true
  servlet:
    multipart:
      max-file-size: 10MB
      max-request-size: 10MB
```

---

## 附录 A：指标评分算法详解

### A.1 CorrectnessScorer — Jaccard 词级相似度

```
1. 全字面匹配 → 1.0
2. 包含关系（actual ⊇ expected 或反之）→ 0.9
3. Jaccard = |expected_words ∩ actual_words| / |expected_words|
```

### A.2 BleuScorer — 词级精确率

```
BLEU = matches / total_actual_words
matches = |{ w ∈ actual_words ∍ ∃ r ∈ expected_words: w = r }|
通过阈值：≥ 0.5
```

### A.3 RougeScorer — LCS 比率

```
ROUGE-L = LCS(a, b) / max(|a|, |b|)
其中 LCS 为最长公共子序列（动态规划，O(n×m)）
通过阈值：≥ 0.4
```

### A.4 SimilarityScorer — Jaccard Trigram

```
grams(s) = { s[i..i+2] | i ∈ [0, len(s)-3] }  （字符三元组集合）
Similarity = |grams(a) ∩ grams(b)| / |grams(a) ∪ grams(b)|
通过阈值：≥ 0.5
```

---

*文档版本：1.0 | 维护者：panris | 项目地址：[github.com/panris/agent-eval-lite](https://github.com/panris/agent-eval-lite)*
