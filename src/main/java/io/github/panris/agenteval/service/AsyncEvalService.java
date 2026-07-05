package io.github.panris.agenteval.service;

import io.github.panris.agenteval.*;
import io.github.panris.agenteval.scorer.builtin.*;
import org.springframework.stereotype.Service;

import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Async evaluation task queue with real-time status tracking.
 */
@Service
public class AsyncEvalService {
    private static final Logger log = LoggerFactory.getLogger(AsyncEvalService.class);

    private final Map<String, TaskStatus> tasks = new ConcurrentHashMap<>();
    private final ExecutorService executor = Executors.newFixedThreadPool(
        Runtime.getRuntime().availableProcessors()
    );
    private final Map<String, Map<String, Object>> reportHistory;
    private final Map<String, String> sharedReports;
    private final ObjectMapper objectMapper;

    public AsyncEvalService(
            Map<String, Map<String, Object>> reportHistory,
            Map<String, String> sharedReports,
            ObjectMapper objectMapper) {
        this.reportHistory = reportHistory;
        this.sharedReports = sharedReports;
        this.objectMapper = objectMapper;
    }

    public static class TaskStatus {
        public String taskId;
        public String status; // PENDING / RUNNING / COMPLETED / FAILED
        public String reportId;
        public String error;
        public long createdAt;
        public long completedAt;
        public int totalCases;
        public int completedCases;

        public TaskStatus(String taskId) {
            this.taskId = taskId;
            this.status = "PENDING";
            this.createdAt = System.currentTimeMillis();
            this.totalCases = 0;
            this.completedCases = 0;
        }
    }

    public String submitTask(List<TestCase> testCases, List<String> metrics, String agentType) {
        String taskId = "task_" + System.currentTimeMillis();
        TaskStatus status = new TaskStatus(taskId);
        status.totalCases = testCases.size();
        tasks.put(taskId, status);

        executor.submit(() -> {
            status.status = "RUNNING";
            try {
                Agent agent = buildAgent(agentType);

                Evaluator.Builder builder = Evaluator.builder();
                for (String metric : metrics) {
                    builder.metrics(metric);
                }
                Evaluator evaluator = builder.build();

                EvaluationReport report = evaluator.evaluate(agent, testCases);

                String reportId = "report_" + System.currentTimeMillis();
                Map<String, Object> reportData = new LinkedHashMap<>();
                reportData.put("summary", report.getSummary());
                reportData.put("evaluations", serializeEvaluations(report.getEvaluations()));
                reportData.put("totalTestCases", report.getTotalTestCases());
                reportData.put("passedTestCases", report.getPassedTestCases());
                reportData.put("failedTestCases", report.getFailedTestCases());
                reportData.put("executionTimeMs", report.getExecutionTimeMs());
                reportData.put("timestamp", System.currentTimeMillis());
                reportData.put("asyncTaskId", taskId);

                reportHistory.put(reportId, reportData);
                saveReportsJson();

                status.reportId = reportId;
                status.status = "COMPLETED";
                status.completedCases = testCases.size();
                status.completedAt = System.currentTimeMillis();
            } catch (Exception e) {
                status.status = "FAILED";
                status.error = e.getMessage();
                status.completedAt = System.currentTimeMillis();
                log.error("Async task {} failed: {}", taskId, e.getMessage(), e);
            }
        });

        return taskId;
    }

    private Agent buildAgent(String agentType) {
        return switch (agentType.toLowerCase()) {
            case "echo" -> Agent.from(input -> input);
            case "upper" -> Agent.from(String::toUpperCase);
            case "reverse" -> Agent.from(input -> new StringBuilder(input).reverse().toString());
            default -> input -> "Demo response for: " + input;
        };
    }

    private List<Map<String, Object>> serializeEvaluations(List<Evaluation> evals) {
        List<Map<String, Object>> result = new ArrayList<>();
        for (Evaluation ev : evals) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("testCaseId", ev.getTestCaseId());
            m.put("overallScore", ev.getOverallScore());
            m.put("passed", ev.isPassed());
            AgentOutput ao = ev.getAgentOutput();
            m.put("output", ao != null && ao.getOutput() != null ? ao.getOutput() : "");
            result.add(m);
        }
        return result;
    }

    private void saveReportsJson() {
        try {
            Path dataFile = Paths.get("data/reports.json");
            Files.createDirectories(dataFile.getParent());
            String json = objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(reportHistory);
            Files.writeString(dataFile, json);
        } catch (Exception e) {
            log.error("Failed to save reports: {}", e.getMessage(), e);
        }
    }

    public TaskStatus getStatus(String taskId) {
        return tasks.get(taskId);
    }

    public List<TaskStatus> getAllStatuses() {
        return new ArrayList<>(tasks.values());
    }

    public void shutdown() {
        executor.shutdown();
    }
}
