package io.github.panris.agenteval.web.dto;

import java.util.Map;

/**
 * 测试用例请求 DTO。
 */
public class TestCaseRequest {
    private String name;
    private String input;
    private String expected;
    private String groupId;
    private String project;
    private String module;
    private String function;
    private String description;
    private Map<String, Object> metadata;

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getInput() { return input; }
    public void setInput(String input) { this.input = input; }
    public String getExpected() { return expected; }
    public void setExpected(String expected) { this.expected = expected; }
    public String getGroupId() { return groupId; }
    public void setGroupId(String groupId) { this.groupId = groupId; }
    public String getProject() { return project; }
    public void setProject(String project) { this.project = project; }
    public String getModule() { return module; }
    public void setModule(String module) { this.module = module; }
    public String getFunction() { return function; }
    public void setFunction(String function) { this.function = function; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public Map<String, Object> getMetadata() { return metadata; }
    public void setMetadata(Map<String, Object> metadata) { this.metadata = metadata; }
}
