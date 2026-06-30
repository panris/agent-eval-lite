package io.github.panris.agenteval;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Quick start example and test.
 */
class QuickStartTest {

    @Test
    @DisplayName("Basic evaluation with correctness metric")
    void testBasicEvaluation() {
        // Create test cases
        List<TestCase> testCases = List.of(
            new TestCase("2+2=?", "4"),
            new TestCase("3*3=?", "9"),
            new TestCase("What is Java?", "Java is a programming language")
        );

        // Create a simple agent
        Agent agent = input -> {
            if (input.equals("2+2=?")) return "4";
            if (input.equals("3*3=?")) return "9";
            return "Java is a programming language";
        };

        // Build evaluator with correctness metric
        Evaluator evaluator = Evaluator.builder()
            .metrics("correctness")
            .build();

        // Run evaluation
        EvaluationReport report = evaluator.evaluate(agent, testCases);

        // Verify results
        assertThat(report).isNotNull();
        assertThat(report.getTotalTestCases()).isEqualTo(3);
        assertThat(report.getPassedTestCases()).isEqualTo(3);
        assertThat(report.getFailedTestCases()).isEqualTo(0);

        // Print summary
        report.printSummary();
    }

    @Test
    @DisplayName("Evaluation with multiple metrics")
    void testMultipleMetrics() {
        List<TestCase> testCases = List.of(
            new TestCase("Hello", "Hi there!")
        );

        Agent agent = input -> "Hi there!";

        Evaluator evaluator = Evaluator.builder()
            .metrics("correctness", "safety", "response_time")
            .build();

        EvaluationReport report = evaluator.evaluate(agent, testCases);

        assertThat(report).isNotNull();
        assertThat(report.getTotalTestCases()).isEqualTo(1);

        report.printSummary();
    }

    @Test
    @DisplayName("Custom scorer example")
    void testCustomScorer() {
        List<TestCase> testCases = List.of(
            new TestCase("Test", "Hello World")
        );

        Agent agent = input -> "Hello World";

        // Custom scorer that checks response length
        Evaluator evaluator = Evaluator.builder()
            .scorer(new io.github.panris.agenteval.scorer.EvaluationScorer() {
                @Override
                public String getName() {
                    return "response_length";
                }

                @Override
                public io.github.panris.agenteval.scorer.ScorerResult evaluate(
                    TestCase testCase, AgentOutput output) {
                    int length = output.getOutput().length();
                    boolean passed = length > 0 && length <= 100;
                    return io.github.panris.agenteval.scorer.ScorerResult.of(
                        passed ? 1.0 : 0.5,
                        passed,
                        "Response length: " + length
                    );
                }
            })
            .build();

        EvaluationReport report = evaluator.evaluate(agent, testCases);

        assertThat(report).isNotNull();
        report.printSummary();
    }
}
