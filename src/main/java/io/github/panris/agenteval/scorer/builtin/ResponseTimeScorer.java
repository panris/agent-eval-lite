package io.github.panris.agenteval.scorer.builtin;

import io.github.panris.agenteval.AgentOutput;
import io.github.panris.agenteval.TestCase;
import io.github.panris.agenteval.scorer.EvaluationScorer;
import io.github.panris.agenteval.scorer.ScorerResult;

import java.util.HashMap;
import java.util.Map;

/**
 * Evaluates the response time of the agent.
 */
public class ResponseTimeScorer implements EvaluationScorer {

    private final long maxTimeMs;
    private final long acceptableTimeMs;

    public ResponseTimeScorer() {
        this(5000, 1000); // max 5s, acceptable 1s
    }

    public ResponseTimeScorer(long maxTimeMs, long acceptableTimeMs) {
        this.maxTimeMs = maxTimeMs;
        this.acceptableTimeMs = acceptableTimeMs;
    }

    @Override
    public String getName() {
        return "response_time";
    }

    @Override
    public String getDescription() {
        return "Evaluates the response time of the agent";
    }

    @Override
    public ScorerResult evaluate(TestCase testCase, AgentOutput output) {
        if (output.hasError()) {
            return ScorerResult.failed("Agent execution failed: " + output.getError().getMessage());
        }

        long timeMs = output.getExecutionTimeMs();

        // Calculate score based on response time
        double score;
        boolean passed;
        String rationale;

        if (timeMs <= acceptableTimeMs) {
            score = 1.0;
            passed = true;
            rationale = String.format("Excellent response time: %d ms (acceptable: %d ms)", timeMs, acceptableTimeMs);
        } else if (timeMs <= maxTimeMs) {
            score = 0.7;
            passed = true;
            rationale = String.format("Acceptable response time: %d ms (max: %d ms)", timeMs, maxTimeMs);
        } else {
            score = 0.0;
            passed = false;
            rationale = String.format("Response time too slow: %d ms (max: %d ms)", timeMs, maxTimeMs);
        }

        Map<String, Object> metadata = new HashMap<>();
        metadata.put("response_time_ms", timeMs);
        metadata.put("max_time_ms", maxTimeMs);
        metadata.put("acceptable_time_ms", acceptableTimeMs);

        return ScorerResult.of(score, passed, rationale, metadata);
    }
}
