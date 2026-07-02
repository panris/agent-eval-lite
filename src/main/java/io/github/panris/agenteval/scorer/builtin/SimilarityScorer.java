package io.github.panris.agenteval.scorer.builtin;

import io.github.panris.agenteval.*;
import io.github.panris.agenteval.scorer.*;
import java.util.*;

/**
 * Semantic similarity using Jaccard on character trigrams.
 */
public class SimilarityScorer implements EvaluationScorer {
    @Override
    public String getName() { return "similarity"; }

    @Override
    public ScorerResult evaluate(TestCase testCase, AgentOutput output) {
        if (output.hasError()) return ScorerResult.failed("Agent execution failed");
        String expected = testCase.getExpectedOutput();
        String actual = output.getOutput();
        if (expected == null || expected.isBlank()) return ScorerResult.failed("No expected output");
        double score = jaccardTrigram(expected, actual);
        String detail = String.format("Similarity: %.2f%%", score * 100);
        return score >= 0.5 ? ScorerResult.passed(score, detail) : ScorerResult.failed(detail);
    }

    private double jaccardTrigram(String a, String b) {
        Set<String> gramsA = getTrigrams(a.toLowerCase());
        Set<String> gramsB = getTrigrams(b.toLowerCase());
        if (gramsA.isEmpty() && gramsB.isEmpty()) return 1.0;
        Set<String> union = new HashSet<>(gramsA);
        union.addAll(gramsB);
        Set<String> intersection = new HashSet<>(gramsA);
        intersection.retainAll(gramsB);
        return (double) intersection.size() / union.size();
    }

    private Set<String> getTrigrams(String s) {
        Set<String> grams = new HashSet<>();
        for (int i = 0; i + 2 < s.length(); i++) {
            grams.add(s.substring(i, i + 3));
        }
        return grams;
    }
}
