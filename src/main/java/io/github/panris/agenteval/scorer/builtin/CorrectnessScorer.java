package io.github.panris.agenteval.scorer.builtin;

import io.github.panris.agenteval.AgentOutput;
import io.github.panris.agenteval.TestCase;
import io.github.panris.agenteval.scorer.EvaluationScorer;
import io.github.panris.agenteval.scorer.ScorerResult;

import java.util.HashSet;
import java.util.Set;

/**
 * Evaluates if the output matches the expected result.
 */
public class CorrectnessScorer implements EvaluationScorer {

    private final double threshold;

    public CorrectnessScorer() {
        this(0.7);
    }

    public CorrectnessScorer(double threshold) {
        this.threshold = threshold;
    }

    @Override
    public String getName() {
        return "correctness";
    }

    @Override
    public String getDescription() {
        return "Evaluates if the output matches the expected result";
    }

    @Override
    public double getThreshold() {
        return threshold;
    }

    @Override
    public ScorerResult evaluate(TestCase testCase, AgentOutput output) {
        if (output.hasError()) {
            return ScorerResult.failed("Agent execution failed: " + output.getError().getMessage());
        }

        String expected = testCase.getExpectedOutput();
        String actual = output.getOutput();

        if (expected == null || actual == null) {
            return ScorerResult.failed("Missing expected or actual output");
        }

        // Calculate similarity score
        double score = calculateSimilarity(expected, actual);
        boolean passed = score >= threshold;

        String rationale = String.format("Similarity: %.2f (threshold: %.2f)", score, threshold);

        return ScorerResult.of(score, passed, rationale);
    }

    private double calculateSimilarity(String expected, String actual) {
        String exp = expected.trim().toLowerCase();
        String act = actual.trim().toLowerCase();

        if (exp.equals(act)) {
            return 1.0;
        }

        if (act.contains(exp) || exp.contains(act)) {
            return 0.9;
        }

        String[] expWords = exp.split("\\s+");
        String[] actWords = act.split("\\s+");

        if (expWords.length == 0 || actWords.length == 0) {
            return 0.0;
        }

        Set<String> actWordSet = new HashSet<>();
        for (String word : actWords) {
            actWordSet.add(word);
        }

        int matches = 0;
        for (String expWord : expWords) {
            if (actWordSet.contains(expWord)) {
                matches++;
            }
        }

        return (double) matches / expWords.length;
    }
}
