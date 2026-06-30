package io.github.panris.agenteval.scorer;

import java.util.HashMap;
import java.util.Map;

/**
 * Result of a single scorer evaluation.
 */
public class ScorerResult {

    private final double score;
    private final boolean passed;
    private final String rationale;
    private final Map<String, Object> metadata;

    private ScorerResult(double score, boolean passed, String rationale, Map<String, Object> metadata) {
        this.score = score;
        this.passed = passed;
        this.rationale = rationale;
        this.metadata = metadata != null ? metadata : new HashMap<>();
    }

    /**
     * Create a successful result.
     */
    public static ScorerResult passed(double score, String rationale) {
        return new ScorerResult(score, true, rationale, null);
    }

    /**
     * Create a failed result.
     */
    public static ScorerResult failed(String rationale) {
        return new ScorerResult(0.0, false, rationale, null);
    }

    /**
     * Create a result with score.
     */
    public static ScorerResult of(double score, boolean passed, String rationale) {
        return new ScorerResult(score, passed, rationale, null);
    }

    /**
     * Create a result with metadata.
     */
    public static ScorerResult of(double score, boolean passed, String rationale, Map<String, Object> metadata) {
        return new ScorerResult(score, passed, rationale, metadata);
    }

    public double getScore() {
        return score;
    }

    public boolean isPassed() {
        return passed;
    }

    public String getRationale() {
        return rationale;
    }

    public Map<String, Object> getMetadata() {
        return metadata;
    }

    @Override
    public String toString() {
        return String.format("ScorerResult{score=%.2f, passed=%s, rationale='%s'}",
            score, passed, rationale);
    }
}
