package io.github.panris.agenteval.model;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Test case group for organizing test cases.
 */
public class TestCaseGroup {

    private String id;
    private String name;
    private String description;
    private List<String> testCaseIds;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public TestCaseGroup() {
        this.id = UUID.randomUUID().toString();
        this.testCaseIds = new ArrayList<>();
        this.createdAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
    }

    public TestCaseGroup(String name, String description) {
        this();
        this.name = name;
        this.description = description;
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

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public List<String> getTestCaseIds() {
        return testCaseIds;
    }

    public void setTestCaseIds(List<String> testCaseIds) {
        this.testCaseIds = testCaseIds;
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

    public void addTestCaseId(String testCaseId) {
        if (!testCaseIds.contains(testCaseId)) {
            testCaseIds.add(testCaseId);
            updateTimestamp();
        }
    }

    public void removeTestCaseId(String testCaseId) {
        testCaseIds.remove(testCaseId);
        updateTimestamp();
    }

    public void updateTimestamp() {
        this.updatedAt = LocalDateTime.now();
    }
}
