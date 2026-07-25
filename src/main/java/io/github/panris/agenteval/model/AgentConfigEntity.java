package io.github.panris.agenteval.model;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "agent_configs")
public class AgentConfigEntity {

    @Id
    @Column(length = 36)
    private String id;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(nullable = false, length = 20)
    private String type;

    @Column(length = 500)
    private String description;

    @Column(nullable = false, length = 500)
    private String endpoint;

    @Column(columnDefinition = "TEXT")
    private String headersJson;

    @Column(nullable = false)
    private int timeout;

    @Column(columnDefinition = "TEXT")
    private String requestMappingJson;

    @Column(columnDefinition = "TEXT")
    private String responseMappingJson;

    @Column(columnDefinition = "TEXT")
    private String configJson;

    @Column(name = "created_at")
    private Instant createdAt;

    @Column(name = "updated_at")
    private Instant updatedAt;

    public AgentConfigEntity() {
        this.timeout = 30000;
        this.createdAt = Instant.now();
        this.updatedAt = Instant.now();
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getType() { return type; }
    public void setType(String type) { this.type = type; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public String getEndpoint() { return endpoint; }
    public void setEndpoint(String endpoint) { this.endpoint = endpoint; }
    public String getHeadersJson() { return headersJson; }
    public void setHeadersJson(String headersJson) { this.headersJson = headersJson; }
    public int getTimeout() { return timeout; }
    public void setTimeout(int timeout) { this.timeout = timeout; }
    public String getRequestMappingJson() { return requestMappingJson; }
    public void setRequestMappingJson(String requestMappingJson) { this.requestMappingJson = requestMappingJson; }
    public String getResponseMappingJson() { return responseMappingJson; }
    public void setResponseMappingJson(String responseMappingJson) { this.responseMappingJson = responseMappingJson; }
    public String getConfigJson() { return configJson; }
    public void setConfigJson(String configJson) { this.configJson = configJson; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
}