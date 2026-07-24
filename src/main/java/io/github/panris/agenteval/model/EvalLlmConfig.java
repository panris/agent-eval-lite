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
        return "你是一个专业的测试评测员。请将期望输出与实际输出进行比较，从0.0到1.0打分，并简要说明评分理由（中文）。\n\n评分标准：\n- 1.0：完全匹配，语义一致，无遗漏\n- 0.8-0.9：核心内容匹配，微小差异\n- 0.6-0.7：部分匹配，有关键信息但存在缺失或错误\n- 0.4-0.5：弱相关，只有少量共同点\n- 0.0-0.3：基本不相关或完全错误\n\n返回格式（严格JSON，不要额外文本）：\n{\"score\": 0.85, \"rationale\": \"评分理由\"}";
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