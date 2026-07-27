package io.github.panris.agenteval;

import java.util.List;
import java.util.Map;

/**
 * Test case for agent evaluation.
 */
public class TestCase {

    private final String id;
    private final String input;
    private final String expectedOutput;
    private final Map<String, Object> context;
    private final Map<String, Object> metadata;
    private final String project;
    private final String module;
    private final String function;

    public TestCase(String input, String expectedOutput) {
        this.id = generateId();
        this.input = input;
        this.expectedOutput = expectedOutput;
        this.context = Map.of();
        this.metadata = Map.of();
        this.project = null;
        this.module = null;
        this.function = null;
    }

    public TestCase(String id, String input, String expectedOutput,
                    Map<String, Object> context, Map<String, Object> metadata) {
        this.id = id != null ? id : generateId();
        this.input = input;
        this.expectedOutput = expectedOutput;
        this.context = context != null ? context : Map.of();
        this.metadata = metadata != null ? metadata : Map.of();
        this.project = null;
        this.module = null;
        this.function = null;
    }

    public TestCase(String id, String input, String expectedOutput,
                    Map<String, Object> context, Map<String, Object> metadata,
                    String project, String module, String function) {
        this.id = id != null ? id : generateId();
        this.input = input;
        this.expectedOutput = expectedOutput;
        this.context = context != null ? context : Map.of();
        this.metadata = metadata != null ? metadata : Map.of();
        this.project = project;
        this.module = module;
        this.function = function;
    }

    private String generateId() {
        return "tc_" + System.currentTimeMillis() + "_" + (int)(Math.random() * 10000);
    }

    public String getId() {
        return id;
    }

    public String getInput() {
        return input;
    }

    public String getExpectedOutput() {
        return expectedOutput;
    }

    public Map<String, Object> getContext() {
        return context;
    }

    public Map<String, Object> getMetadata() {
        return metadata;
    }

    public String getProject() {
        return project;
    }

    public String getModule() {
        return module;
    }

    public String getFunction() {
        return function;
    }

    @Override
    public String toString() {
        return String.format("TestCase{id='%s', input='%s'}", id, input);
    }
}
