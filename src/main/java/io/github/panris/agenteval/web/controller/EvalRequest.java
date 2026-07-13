package io.github.panris.agenteval.web.controller;

import java.util.List;
import java.util.Map;

public class EvalRequest {
    private String agentType;
    private Map<String, Object> agentConfig;
    private List<String> metrics;
    private List<TestCaseDto> testCases;
    /** 测试用例 ID 列表（用于 async endpoint）；若同时传 testCases 则优先用 testCases */
    private List<String> caseIds;
    /** 报告所属分组名称，用于报告历史过滤 */
    private String group;
    /** 三维分组：项目（评测时按维度筛选） */
    private String project;
    /** 三维分组：模块 */
    private String module;
    /** 三维分组：功能 */
    private String function;

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

    public List<String> getCaseIds() {
        return caseIds;
    }

    public void setCaseIds(List<String> caseIds) {
        this.caseIds = caseIds;
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
