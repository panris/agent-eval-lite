package io.github.panris.agenteval.web.controller;

import java.util.List;
import java.util.Map;

public class EvalRequest {
    private String agentType;
    private Map<String, Object> agentConfig;
    private List<String> metrics;
    private List<TestCaseDto> testCases;
    /** 报告所属分组名称，用于报告历史过滤 */
    private String group;

    // Getters and Setters
    public String getAgentType() {
        return agentType;
    }

    public void setAgentType(String agentType) {
        this.agentType = agentType;
    }

    public Map<String, Object> getAgentConfig() {
        return agentConfig;
    }

    public void setAgentConfig(Map<String, Object> agentConfig) {
        this.agentConfig = agentConfig;
    }

    public List<String> getMetrics() {
        return metrics;
    }

    public void setMetrics(List<String> metrics) {
        this.metrics = metrics;
    }

    public List<TestCaseDto> getTestCases() {
        return testCases;
    }

    public void setTestCases(List<TestCaseDto> testCases) {
        this.testCases = testCases;
    }

    public String getGroup() {
        return group;
    }

    public void setGroup(String group) {
        this.group = group;
    }
}

class TestCaseDto {
    private String input;
    private String expected;

    // Getters and Setters
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
}
