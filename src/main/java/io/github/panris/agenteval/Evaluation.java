package io.github.panris.agenteval;

import io.github.panris.agenteval.scorer.ScorerResult;

import java.util.Map;

/**
 * Evaluation result for a single test case.
 */
public class Evaluation {

    private final String testCaseId;
    private final String testCaseName;  // nullable, for readable PDF display
    private final AgentOutput agentOutput;
    private final Map<String, ScorerResult> scorerResults;
    private final boolean passed;
    private final double overallScore;

    public Evaluation(String testCaseId, String testCaseName, AgentOutput agentOutput, Map<String, ScorerResult> scorerResults) {
        this.testCaseId = testCaseId;
        this.testCaseName = testCaseName;
        this.agentOutput = agentOutput;
        this.scorerResults = scorerResults;
        this.passed = scorerResults.values().stream().allMatch(ScorerResult::isPassed);
        this.overallScore = scorerResults.values().stream()
            .mapToDouble(ScorerResult::getScore)
            .average()
            .orElse(0.0);
    }

    /** Backward-compatible constructor (testCaseName = null, resolved later via repository lookup). */
    public Evaluation(String testCaseId, AgentOutput agentOutput, Map<String, ScorerResult> scorerResults) {
        this(testCaseId, null, agentOutput, scorerResults);
    }

    public String getTestCaseId() {
        return testCaseId;
    }

    public String getTestCaseName() {
        return testCaseName;
    }

    public Map<String, ScorerResult> getScorerResults() {
        return scorerResults;
    }

    public AgentOutput getAgentOutput() {
        return agentOutput;
    }

    public boolean isPassed() {
        return passed;
    }

    public double getOverallScore() {
        return overallScore;
    }

    @Override
    public String toString() {
        return String.format("Evaluation{testCaseId='%s', testCaseName='%s', passed=%s, score=%.2f}",
            testCaseId, testCaseName, passed, overallScore);
    }
}
