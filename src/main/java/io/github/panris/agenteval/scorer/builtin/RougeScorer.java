package io.github.panris.agenteval.scorer.builtin;

import io.github.panris.agenteval.*;
import io.github.panris.agenteval.scorer.*;

/**
 * ROUGE-L (Longest Common Subsequence) metric.
 */
public class RougeScorer implements EvaluationScorer {
    @Override
    public String getName() { return "rouge"; }

    @Override
    public ScorerResult evaluate(TestCase testCase, AgentOutput output) {
        if (output.hasError()) return ScorerResult.failed("Agent execution failed");
        String expected = testCase.getExpectedOutput();
        String actual = output.getOutput();
        if (expected == null || expected.isBlank()) return ScorerResult.failed("No expected output");
        double score = lcsRatio(expected.toLowerCase(), actual.toLowerCase());
        String detail = String.format("ROUGE-L: %.2f%%", score * 100);
        return score >= 0.4 ? ScorerResult.passed(score, detail) : ScorerResult.failed(detail);
    }

    private double lcsRatio(String a, String b) {
        int[][] dp = new int[a.length() + 1][b.length() + 1];
        for (int i = 1; i <= a.length(); i++) {
            for (int j = 1; j <= b.length(); j++) {
                if (a.charAt(i - 1) == b.charAt(j - 1)) {
                    dp[i][j] = dp[i - 1][j - 1] + 1;
                } else {
                    dp[i][j] = Math.max(dp[i - 1][j], dp[i][j - 1]);
                }
            }
        }
        int lcs = dp[a.length()][b.length()];
        return (double) lcs / Math.max(a.length(), 1);
    }
}
