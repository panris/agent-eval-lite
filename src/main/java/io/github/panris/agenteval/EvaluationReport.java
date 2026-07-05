package io.github.panris.agenteval;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Map;

/**
 * Evaluation report containing results for all test cases.
 */
public class EvaluationReport {

    private static final Logger log = LoggerFactory.getLogger(EvaluationReport.class);

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
        log.info("=== Evaluation Report ===");
        log.info("Total Test Cases: {}, Passed: {}, Failed: {}",
            totalTestCases, passedTestCases, failedTestCases);
        log.info("Pass Rate: {}, Average Score: {}, Execution Time: {}ms",
            String.format("%.2f%%", summary.get("pass_rate")),
            String.format("%.2f", summary.get("average_score")),
            executionTimeMs);

        if (failedTestCases > 0) {
            log.warn("Failed Test Cases: {}",
                evaluations.stream()
                    .filter(e -> !e.isPassed())
                    .map(Evaluation::getTestCaseId)
                    .toList());
        }
    }
}
