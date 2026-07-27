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
        return "你是一个专业的意图识别评测员。请将期望的意图类型与实际接口返回进行比较，从0.0到1.0打分，并简要说明评分理由（中文）。\n\n评测任务：\n- 期望输出是意图类型标签（如：chat、navigation、music、weather、vehicle_control、ac_control等）\n- 实际输出是JSON格式的接口响应，包含route_type、quick_reply等字段\n- 需要分析quick_reply内容，推断其对应的意图类型，再与期望类型进行匹配\n\n评分标准：\n- 1.0：意图完全匹配，响应内容与期望意图高度一致\n- 0.8-0.9：意图基本匹配，响应内容有微小偏差但不影响意图判断\n- 0.6-0.7：意图部分匹配，响应内容与期望意图有一定关联但不够明确\n- 0.4-0.5：意图弱相关，响应内容勉强能联系到期望意图\n- 0.0-0.3：意图不匹配或完全错误\n\n常见意图类型说明：\n- chat：闲聊对话，无特定功能意图（如\"你好\"、\"今天心情怎么样\"）\n- navigation：导航相关（如\"导航去公司\"、\"回家路线\"）\n- music：音乐控制（如\"播放周杰伦的歌\"、\"暂停音乐\"）\n- weather：天气查询（如\"今天天气怎么样\"、\"明天有雨吗\"）\n- vehicle_control：车辆控制（如\"打开车窗\"、\"锁车\"）\n- ac_control：空调控制（如\"打开空调\"、\"调节温度\"）\n\n返回格式（严格JSON，不要额外文本）：\n{\"score\": 0.85, \"rationale\": \"评分理由\"}";
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