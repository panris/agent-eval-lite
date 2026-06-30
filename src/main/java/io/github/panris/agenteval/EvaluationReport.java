package io.github.panris.agenteval;

import java.util.List;
import java.util.Map;

/**
 * Evaluation report containing results for all test cases.
 */
public class EvaluationReport {

    private final List<Evaluation> evaluations;
    private final Map<String, Double> summary;
    private final int totalTestCases;
    private final int passedTestCases;
    private final int failedTestCases;
    private final long executionTimeMs;

    public EvaluationReport(List<Evaluation> evaluations, long executionTimeMs) {
        this.evaluations = evaluations;
        this.executionTimeMs = executionTimeMs;
        this.totalTestCases = evaluations.size();
        this.passedTestCases = (int) evaluations.stream().filter(Evaluation::isPassed).count();
        this.failedTestCases = totalTestCases - passedTestCases;
        this.summary = calculateSummary();
    }

    private Map<String, Double> calculateSummary() {
        double avgScore = evaluations.stream()
            .mapToDouble(Evaluation::getOverallScore)
            .average()
            .orElse(0.0);

        double passRate = totalTestCases > 0
            ? (double) passedTestCases / totalTestCases * 100
            : 0.0;

        return Map.of(
            "average_score", avgScore,
            "pass_rate", passRate,
            "total_test_cases", (double) totalTestCases,
            "passed_test_cases", (double) passedTestCases,
            "failed_test_cases", (double) failedTestCases
        );
    }

    public List<Evaluation> getEvaluations() {
        return evaluations;
    }

    public Map<String, Double> getSummary() {
        return summary;
    }

    public int getTotalTestCases() {
        return totalTestCases;
    }

    public int getPassedTestCases() {
        return passedTestCases;
    }

    public int getFailedTestCases() {
        return failedTestCases;
    }

    public long getExecutionTimeMs() {
        return executionTimeMs;
    }

    public void printSummary() {
        System.out.println("\n=== Evaluation Report ===");
        System.out.printf("Total Test Cases: %d%n", totalTestCases);
        System.out.printf("Passed: %d | Failed: %d%n", passedTestCases, failedTestCases);
        System.out.printf("Pass Rate: %.2f%%%n", summary.get("pass_rate"));
        System.out.printf("Average Score: %.2f%n", summary.get("average_score"));
        System.out.printf("Execution Time: %d ms%n", executionTimeMs);
        System.out.println("========================\n");

        if (failedTestCases > 0) {
            System.out.println("Failed Test Cases:");
            evaluations.stream()
                .filter(e -> !e.isPassed())
                .forEach(e -> System.out.println("  - " + e.getTestCaseId()));
        }
    }
}
