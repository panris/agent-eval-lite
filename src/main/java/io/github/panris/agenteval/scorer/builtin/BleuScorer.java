package io.github.panris.agenteval.scorer.builtin;

import io.github.panris.agenteval.*;
import io.github.panris.agenteval.scorer.*;

/**
 * BLEU score metric (word-level precision approximation).
 */
public class BleuScorer implements EvaluationScorer {
    @Override
    public String getName() { return "bleu"; }

    @Override
    public ScorerResult evaluate(TestCase testCase, AgentOutput output) {
        if (output.hasError()) return ScorerResult.failed("Agent execution failed: " + output.getError().getMessage());
        String expected = testCase.getExpectedOutput();
        String actual = output.getOutput();
        if (expected == null || expected.isBlank()) return ScorerResult.failed("No expected output");
        double score = wordPrecision(expected, actual);
        String detail = String.format("BLEU: %.2f%%", score * 100);
        return score >= 0.5 ? ScorerResult.passed(score, detail) : ScorerResult.failed(detail);
    }

    /** Word-level precision (1-gram overlap) */
    private double wordPrecision(String ref, String hyp) {
        String[] refWords = ref.toLowerCase().split("\\s+");
        String[] hypWords = hyp.toLowerCase().split("\\s+");
        if (hypWords.length == 0) return 0.0;
        long matches = 0;
        for (String w : hypWords) {
            for (String r : refWords) {
                if (w.equals(r)) { matches++; break; }
            }
        }
        return (double) matches / hypWords.length;
    }
}
