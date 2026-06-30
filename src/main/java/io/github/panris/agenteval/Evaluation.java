package io.github.panris.agenteval;

import io.github.panris.agenteval.scorer.ScorerResult;

import java.util.Map;

/**
 * Evaluation result for a single test case.
 */
public class Evaluation {

    private final String testCaseId;
    private final Map<String, ScorerResult> scorerResults;
    private final boolean passed;
    private final double overallScore;

    public Evaluation(String testCaseId, Map<String, ScorerResult> scorerResults) {
        this.testCaseId = testCaseId;
        this.scorerResults = scorerResults;
        this.passed = scorerResults.values().stream().allMatch(ScorerResult::isPassed);
        this.overallScore = scorerResults.values().stream()
            .mapToDouble(ScorerResult::getScore)
            .average()
            .orElse(0.0);
    }

    public String getTestCaseId() {
        return testCaseId;
    }

    public Map<String, ScorerResult> getScorerResults() {
        return scorerResults;
    }

    public boolean isPassed() {
        return passed;
    }

    public double getOverallScore() {
        return overallScore;
    }

    @Override
    public String toString() {
        return String.format("Evaluation{testCaseId='%s', passed=%s, score=%.2f}",
            testCaseId, passed, overallScore);
    }
}
