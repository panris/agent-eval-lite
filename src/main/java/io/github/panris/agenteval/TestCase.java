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

    public TestCase(String input, String expectedOutput) {
        this.id = generateId();
        this.input = input;
        this.expectedOutput = expectedOutput;
        this.context = Map.of();
        this.metadata = Map.of();
    }

    public TestCase(String id, String input, String expectedOutput,
                    Map<String, Object> context, Map<String, Object> metadata) {
        this.id = id != null ? id : generateId();
        this.input = input;
        this.expectedOutput = expectedOutput;
        this.context = context != null ? context : Map.of();
        this.metadata = metadata != null ? metadata : Map.of();
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

    @Override
    public String toString() {
        return String.format("TestCase{id='%s', input='%s'}", id, input);
    }
}
