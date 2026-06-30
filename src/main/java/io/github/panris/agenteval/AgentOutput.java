package io.github.panris.agenteval;

import java.util.Map;

/**
 * Output from agent execution.
 */
public class AgentOutput {

    private final String output;
    private final Map<String, Object> metadata;
    private final long executionTimeMs;
    private final Throwable error;

    public AgentOutput(String output, Map<String, Object> metadata, long executionTimeMs) {
        this.output = output;
        this.metadata = metadata != null ? metadata : Map.of();
        this.executionTimeMs = executionTimeMs;
        this.error = null;
    }

    public AgentOutput(Throwable error) {
        this.output = null;
        this.metadata = Map.of();
        this.executionTimeMs = 0;
        this.error = error;
    }

    public String getOutput() {
        return output;
    }

    public Map<String, Object> getMetadata() {
        return metadata;
    }

    public long getExecutionTimeMs() {
        return executionTimeMs;
    }

    public boolean hasError() {
        return error != null;
    }

    public Throwable getError() {
        return error;
    }

    @Override
    public String toString() {
        if (hasError()) {
            return String.format("AgentOutput{error=%s}", error.getMessage());
        }
        return String.format("AgentOutput{output='%s', time=%dms}", output, executionTimeMs);
    }
}
