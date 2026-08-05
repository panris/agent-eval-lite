package io.github.panris.agenteval.model;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "eval_dimension_configs")
public class EvalDimensionConfig {

    @Id
    @Column(length = 36)
    private String id;

    @Column(name = "`level`", nullable = false, length = 20)
    @Enumerated(EnumType.STRING)
    private DimensionLevel level;

    @Column(length = 100)
    private String project;

    @Column(length = 100)
    private String module;

    @Column(name = "`function`", length = 100)
    private String function;

    @Column(length = 36)
    private String modelId;

    @Column(columnDefinition = "TEXT")
    private String systemPrompt;

    @Column(nullable = false)
    private double passThreshold = 0.7;

    @Column(length = 500)
    private String description;

    @Column(name = "created_at")
    private Instant createdAt;

    @Column(name = "updated_at")
    private Instant updatedAt;

    public enum DimensionLevel {
        GLOBAL,
        PROJECT,
        MODULE,
        FUNCTION
    }

    public EvalDimensionConfig() {
        this.id = UUID.randomUUID().toString();
        this.createdAt = Instant.now();
        this.updatedAt = Instant.now();
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public DimensionLevel getLevel() { return level; }
    public void setLevel(DimensionLevel level) { this.level = level; }
    public String getProject() { return project; }
    public void setProject(String project) { this.project = project; }
    public String getModule() { return module; }
    public void setModule(String module) { this.module = module; }
    public String getFunction() { return function; }
    public void setFunction(String function) { this.function = function; }
    public String getModelId() { return modelId; }
    public void setModelId(String modelId) { this.modelId = modelId; }
    public String getSystemPrompt() { return systemPrompt; }
    public void setSystemPrompt(String systemPrompt) { this.systemPrompt = systemPrompt; }
    public double getPassThreshold() { return passThreshold; }
    public void setPassThreshold(double passThreshold) { this.passThreshold = passThreshold; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
}