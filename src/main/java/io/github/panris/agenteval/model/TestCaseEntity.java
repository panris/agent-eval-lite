package io.github.panris.agenteval.model;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Test case entity with metadata.
 */
public class TestCaseEntity {

    private String id;
    private String name;
    private String input;
    private String expected;
    private String groupId;
    private Map<String, Object> metadata;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public TestCaseEntity() {
        this.id = UUID.randomUUID().toString();
        this.metadata = new HashMap<>();
        this.createdAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
    }

    public TestCaseEntity(String name, String input, String expected) {
        this();
        this.name = name;
        this.input = input;
        this.expected = expected;
    }

    // Getters and Setters
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

    public Map<String, Object> getMetadata() {
        return metadata;
    }

    public void setMetadata(Map<String, Object> metadata) {
        this.metadata = metadata;
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

    public void updateTimestamp() {
        this.updatedAt = LocalDateTime.now();
    }
    
    @SuppressWarnings("unchecked")
    public java.util.List<String> getTags() {
        Object tags = metadata.get("tags");
        if (tags instanceof java.util.List) {
            return (java.util.List<String>) tags;
        }
        return new java.util.ArrayList<>();
    }
    
    @SuppressWarnings("unchecked")
    public void setTags(java.util.List<String> tags) {
        metadata.put("tags", tags != null ? tags : new java.util.ArrayList<>());
    }
}
