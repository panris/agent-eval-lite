package io.github.panris.agenteval.service;

import io.github.panris.agenteval.*;
import io.github.panris.agenteval.service.ReportService;
import io.github.panris.agenteval.scorer.ScorerResult;
import org.springframework.beans.factory.annotation.Qualifier;
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
    private final Deque<String> taskOrder = new ConcurrentLinkedDeque<>();
    private final Executor executor;
    private final ReportService reportService;
    private final ObjectMapper objectMapper;
    private static final int MAX_TASKS = 1000;

    public AsyncEvalService(ReportService reportService, ObjectMapper objectMapper,
                            @Qualifier("evalTaskExecutor") Executor evalTaskExecutor) {
        this.reportService = reportService;
        this.objectMapper = objectMapper;
        this.executor = evalTaskExecutor;
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
        taskOrder.addLast(taskId);
        evictOldTasks();

        executor.execute(() -> {
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
                reportData.put("evaluations", serializeEvaluations(report.getEvaluations(), testCases));
                reportData.put("totalTestCases", report.getTotalTestCases());
                reportData.put("passedTestCases", report.getPassedTestCases());
                reportData.put("failedTestCases", report.getFailedTestCases());
                reportData.put("executionTimeMs", report.getExecutionTimeMs());
                reportData.put("timestamp", System.currentTimeMillis());
                reportData.put("asyncTaskId", taskId);

                reportService.saveReport(reportId, reportData);

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

    public List<Map<String, Object>> serializeEvaluations(List<Evaluation> evals, List<TestCase> testCases) {
        Map<String, TestCase> tcMap = new LinkedHashMap<>();
        for (TestCase tc : testCases) {
            tcMap.put(tc.getId(), tc);
        }
        List<Map<String, Object>> result = new ArrayList<>();
        for (Evaluation ev : evals) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("testCaseId", ev.getTestCaseId());
            TestCase tc = tcMap.get(ev.getTestCaseId());
            m.put("testCaseInput", tc != null && tc.getInput() != null ? tc.getInput() : "");
            m.put("overallScore", ev.getOverallScore());
            m.put("passed", ev.isPassed());
            AgentOutput ao = ev.getAgentOutput();
            m.put("output", ao != null && ao.getOutput() != null ? ao.getOutput() : "");

            Map<String, Object> srMap = new LinkedHashMap<>();
            Map<String, ScorerResult> src = ev.getScorerResults();
            if (src != null) {
                for (Map.Entry<String, ScorerResult> se : src.entrySet()) {
                    Map<String, Object> sr = new LinkedHashMap<>();
                    sr.put("score", se.getValue().getScore());
                    sr.put("passed", se.getValue().isPassed());
                    sr.put("rationale", se.getValue().getRationale());
                    srMap.put(se.getKey(), sr);
                }
            }
            m.put("scorerResults", srMap);

            result.add(m);
        }
        return result;
    }

    /** 超出上限时按提交顺序淘汰最旧的任务，避免 tasks 无限增长造成内存泄漏 */
    private void evictOldTasks() {
        while (tasks.size() > MAX_TASKS) {
            String oldest = taskOrder.pollFirst();
            if (oldest == null) break;
            tasks.remove(oldest);
        }
    }

    public TaskStatus getStatus(String taskId) {
        return tasks.get(taskId);
    }

    public List<TaskStatus> getAllStatuses() {
        return new ArrayList<>(tasks.values());
    }
}