package io.github.panris.agenteval.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.Instant;
import java.util.UUID;

public class EvalLlmConfig {
    private String id;
    @JsonProperty("name") private String name;
    @JsonProperty("baseUrl") private String baseUrl;
    @JsonProperty("apiKey") private String apiKey;
    @JsonProperty("model") private String model;
    @JsonProperty("temperature") private double temperature = 0.1;
    @JsonProperty("maxTokens") private int maxTokens = 256;
    @JsonProperty("timeout") private int timeout = 30000;
    @JsonProperty("passThreshold") private double passThreshold = 0.7;
    @JsonProperty("systemPrompt") private String systemPrompt;
    @JsonProperty("createdAt") private Instant createdAt;
    @JsonProperty("updatedAt") private Instant updatedAt;

    public EvalLlmConfig() {
        this.id = UUID.randomUUID().toString();
        this.createdAt = Instant.now();
        this.updatedAt = Instant.now();
        this.systemPrompt = buildDefaultSystemPrompt();
    }

    public static String buildDefaultSystemPrompt() {
        return """
你是一名精准的车辆控制意图识别评测专家。

## 任务
对比实际输出与期望输出，提取关键字段并评分。

## 输入数据
用户会提供：
- 【实际输出】：JSON格式的模型响应
- 【期望输出】：JSON格式的正确答案

## 评分步骤（严格按此执行）

### 步骤1：解析JSON，提取核心字段
从实际输出和期望输出中分别提取：
- route_type（路由类型）：最重要字段
- emotion_tag（情绪标签）
- vpa_tag（VPA标签）
- quick_reply（快速回复文本）

### 步骤2：逐项对比评分

#### route_type（权重70%）
对比实际的 route_type 与期望的 route_type：
- 完全一致 → 得 0.7 分
- 不一致 → 得 0 分，最终总分 ≤ 0.3

#### emotion_tag（权重15%）
对比实际的 emotion_tag 与期望的 emotion_tag：
- 完全一致 → 得 0.15 分
- 不一致 → 得 0 分

#### vpa_tag（权重15%）
对比实际的 vpa_tag 与期望的 vpa_tag：
- 完全一致 → 得 0.15 分
- 不一致 → 得 0 分

## 最终评分规则
1. route_type 一致 + 所有标签一致 → 1.0 分
2. route_type 一致 + emotion_tag 不一致 + vpa_tag 一致 → 0.85 分
3. route_type 一致 + emotion_tag 一致 + vpa_tag 不一致 → 0.85 分
4. route_type 一致 + 两个标签都不一致 → 0.7 分
5. route_type 不一致 → 0.0 分

## 输出格式（严格JSON）
返回一个JSON对象，格式如下：
{
  "score": 0.85,
  "rationale": "route_type一致(simple)，emotion_tag不一致(期望normal/实际happy)，vpa_tag一致(normal)"
}

注意：
- score 保留两位小数
- rationale 简要说明各字段对比结果
- 只输出JSON，不要其他文本
""";
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getBaseUrl() { return baseUrl; }
    public void setBaseUrl(String baseUrl) { this.baseUrl = baseUrl; }
    public String getApiKey() { return apiKey; }
    public void setApiKey(String apiKey) { this.apiKey = apiKey; }
    public String getModel() { return model; }
    public void setModel(String model) { this.model = model; }
    public double getTemperature() { return temperature; }
    public void setTemperature(double temperature) { this.temperature = temperature; }
    public int getMaxTokens() { return maxTokens; }
    public void setMaxTokens(int maxTokens) { this.maxTokens = maxTokens; }
    public int getTimeout() { return timeout; }
    public void setTimeout(int timeout) { this.timeout = timeout; }
    public double getPassThreshold() { return passThreshold; }
    public void setPassThreshold(double passThreshold) { this.passThreshold = passThreshold; }
    public String getSystemPrompt() { return systemPrompt; }
    public void setSystemPrompt(String systemPrompt) { this.systemPrompt = systemPrompt; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
}