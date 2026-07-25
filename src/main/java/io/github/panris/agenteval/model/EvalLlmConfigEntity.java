package io.github.panris.agenteval.model;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "eval_llm_configs")
public class EvalLlmConfigEntity {

    @Id
    @Column(length = 36)
    private String id;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(name = "base_url", length = 500)
    private String baseUrl;

    @Column(name = "api_key", length = 200)
    private String apiKey;

    @Column(length = 100)
    private String model;

    @Column(nullable = false)
    private double temperature = 0.1;

    @Column(name = "max_tokens", nullable = false)
    private int maxTokens = 256;

    @Column(nullable = false)
    private int timeout = 30000;

    @Column(name = "pass_threshold", nullable = false)
    private double passThreshold = 0.7;

    @Column(name = "system_prompt", columnDefinition = "TEXT")
    private String systemPrompt;

    @Column(name = "created_at")
    private Instant createdAt;

    @Column(name = "updated_at")
    private Instant updatedAt;

    public EvalLlmConfigEntity() {
        this.createdAt = Instant.now();
        this.updatedAt = Instant.now();
        this.systemPrompt = EvalLlmConfig.buildDefaultSystemPrompt();
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