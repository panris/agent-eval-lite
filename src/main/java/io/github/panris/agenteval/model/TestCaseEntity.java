package io.github.panris.agenteval.model;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Test case entity with metadata.
 */
@Entity
@Table(name = "test_cases")
public class TestCaseEntity {

    @Id
    @Column(length = 36)
    private String id;

    @Size(max = 100, message = "用例名称长度不能超过100字符")
    @Column(length = 100)
    private String name;

    @NotBlank(message = "测试输入不能为空")
    @Size(max = 10000, message = "测试输入长度不能超过10000字符")
    @Column(columnDefinition = "TEXT")
    private String input;

    @Size(max = 10000, message = "期望输出长度不能超过10000字符")
    @Column(columnDefinition = "TEXT")
    private String expected;
    
    @Column(length = 36)
    private String groupId;
    
    @Column(length = 100)
    private String project;
    
    @Column(length = 100)
    private String module;
    
    @Column(name = "`function`", length = 100)
    private String function;
    
    @Column(length = 500)
    private String description;
    
    @Column(columnDefinition = "TEXT")
    private String metadataJson;
    
    @Column(name = "created_at")
    private LocalDateTime createdAt;
    
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
    
    @Column(columnDefinition = "BOOLEAN DEFAULT FALSE")
    private Boolean deleted;
    
    @Column(name = "deleted_at")
    private LocalDateTime deletedAt;

    @Transient
    private Map<String, Object> metadata;

    private static final ObjectMapper objectMapper = new ObjectMapper();

    public TestCaseEntity() {
        this.id = UUID.randomUUID().toString();
        this.metadata = new HashMap<>();
        this.createdAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
        this.deleted = false;
    }

    public TestCaseEntity(String name, String input, String expected) {
        this();
        this.name = name;
        this.input = input;
        this.expected = expected;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getInput() {
        return input;
    }

    public void setInput(String input) {
        this.input = input;
    }

    public String getExpected() {
        return expected;
    }

    public void setExpected(String expected) {
        this.expected = expected;
    }

    public String getGroupId() {
        return groupId;
    }

    public void setGroupId(String groupId) {
        this.groupId = groupId;
    }

    public String getProject() {
        return project;
    }

    public void setProject(String project) {
        this.project = project;
    }

    public String getModule() {
        return module;
    }

    public void setModule(String module) {
        this.module = module;
    }

    public String getFunction() {
        return function;
    }

    public void setFunction(String function) {
        this.function = function;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getMetadataJson() {
        return metadataJson;
    }

    public void setMetadataJson(String metadataJson) {
        this.metadataJson = metadataJson;
        this.metadata = null;
    }

    public Map<String, Object> getMetadata() {
        if (metadata == null) {
            if (metadataJson != null && !metadataJson.isEmpty()) {
                try {
                    metadata = objectMapper.readValue(metadataJson, new TypeReference<Map<String, Object>>() {});
                } catch (JsonProcessingException e) {
                    metadata = new HashMap<>();
                }
            } else {
                metadata = new HashMap<>();
            }
        }
        return metadata;
    }

    public void setMetadata(Map<String, Object> metadata) {
        this.metadata = metadata != null ? metadata : new HashMap<>();
        try {
            this.metadataJson = objectMapper.writeValueAsString(this.metadata);
        } catch (JsonProcessingException e) {
            this.metadataJson = "{}";
        }
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }

    public Boolean getDeleted() {
        return deleted;
    }

    public void setDeleted(Boolean deleted) {
        this.deleted = deleted;
    }

    public LocalDateTime getDeletedAt() {
        return deletedAt;
    }

    public void setDeletedAt(LocalDateTime deletedAt) {
        this.deletedAt = deletedAt;
    }

    public void updateTimestamp() {
        this.updatedAt = LocalDateTime.now();
    }
    
    @SuppressWarnings("unchecked")
    public List<String> getTags() {
        Object tags = getMetadata().get("tags");
        if (tags instanceof List) {
            return (List<String>) tags;
        }
        return new ArrayList<>();
    }
    
    public void setTags(List<String> tags) {
        Map<String, Object> meta = getMetadata();
        meta.put("tags", tags != null ? tags : new ArrayList<>());
        setMetadata(meta);
    }
}