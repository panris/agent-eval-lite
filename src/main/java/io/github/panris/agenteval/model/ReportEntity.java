package io.github.panris.agenteval.model;

import jakarta.persistence.*;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "reports")
public class ReportEntity {

    @Id
    @Column(length = 50)
    private String id;

    @Column(columnDefinition = "TEXT")
    private String summaryJson;

    @Column(columnDefinition = "TEXT")
    private String evaluationsJson;

    @Column(name = "total_test_cases")
    private Integer totalTestCases;

    @Column(name = "passed_test_cases")
    private Integer passedTestCases;

    @Column(name = "failed_test_cases")
    private Integer failedTestCases;

    @Column(name = "execution_time_ms")
    private Long executionTimeMs;

    private Long timestamp;

    private Boolean favorite;

    @Column(columnDefinition = "TEXT")
    private String tagsJson;

    private String note;

    @Column(name = "report_group")
    private String group;

    private String project;

    private String module;

    private String function;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    public ReportEntity() {
        this.id = "report_" + System.currentTimeMillis();
        this.createdAt = LocalDateTime.now();
    }

    public ReportEntity(String id) {
        this.id = id;
        this.createdAt = LocalDateTime.now();
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getSummaryJson() {
        return summaryJson;
    }

    public void setSummaryJson(String summaryJson) {
        this.summaryJson = summaryJson;
    }

    public String getEvaluationsJson() {
        return evaluationsJson;
    }

    public void setEvaluationsJson(String evaluationsJson) {
        this.evaluationsJson = evaluationsJson;
    }

    public Integer getTotalTestCases() {
        return totalTestCases;
    }

    public void setTotalTestCases(Integer totalTestCases) {
        this.totalTestCases = totalTestCases;
    }

    public Integer getPassedTestCases() {
        return passedTestCases;
    }

    public void setPassedTestCases(Integer passedTestCases) {
        this.passedTestCases = passedTestCases;
    }

    public Integer getFailedTestCases() {
        return failedTestCases;
    }

    public void setFailedTestCases(Integer failedTestCases) {
        this.failedTestCases = failedTestCases;
    }

    public Long getExecutionTimeMs() {
        return executionTimeMs;
    }

    public void setExecutionTimeMs(Long executionTimeMs) {
        this.executionTimeMs = executionTimeMs;
    }

    public Long getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(Long timestamp) {
        this.timestamp = timestamp;
    }

    public Boolean getFavorite() {
        return favorite;
    }

    public void setFavorite(Boolean favorite) {
        this.favorite = favorite;
    }

    public String getTagsJson() {
        return tagsJson;
    }

    public void setTagsJson(String tagsJson) {
        this.tagsJson = tagsJson;
    }

    public String getNote() {
        return note;
    }

    public void setNote(String note) {
        this.note = note;
    }

    public String getGroup() {
        return group;
    }

    public void setGroup(String group) {
        this.group = group;
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

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }
}