package io.github.panris.agenteval.service;

import io.github.panris.agenteval.*;
import io.github.panris.agenteval.agent.AgentFactory;
import io.github.panris.agenteval.model.EvalLlmConfig;
import io.github.panris.agenteval.repository.EvalLlmConfigRepository;
import io.github.panris.agenteval.service.ReportService;
import io.github.panris.agenteval.repository.TestCaseRepository;
import io.github.panris.agenteval.scorer.ScorerResult;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 异步评测任务队列，支持实时状态跟踪和任务级超时强制终止。
 * 后台每 5 秒扫描 RUNNING 任务，超时则 cancel(true) 中断线程并标记为 TIMED_OUT。
 */
@Service
public class AsyncEvalService {
    private static final Logger log = LoggerFactory.getLogger(AsyncEvalService.class);

    /** 默认任务超时（秒），可通过 submitTask(testCases, metrics, agentType, timeoutSeconds) 覆盖 */
    private static final int DEFAULT_TIMEOUT_SECONDS = 300;

    private final Map<String, TaskStatus> tasks = new ConcurrentHashMap<>();
    private final Deque<String> taskOrder = new ConcurrentLinkedDeque<>();
    private final Executor executor;
    private final ExecutorService evalExecutorService;
    private final ReportService reportService;
    private final TestCaseRepository testCaseRepository;
    private final ObjectMapper objectMapper;
    private final AgentFactory agentFactory;
    private static final int MAX_TASKS = 1000;

    private final EvalLlmConfigRepository evalLlmConfigRepository;

    public AsyncEvalService(ReportService reportService, TestCaseRepository testCaseRepository, ObjectMapper objectMapper,
                            @Qualifier("evalTaskExecutor") Executor evalTaskExecutor,
                            @Qualifier("evalExecutorService") ExecutorService evalExecutorService,
                            AgentFactory agentFactory,
                            EvalLlmConfigRepository evalLlmConfigRepository) {
        this.reportService = reportService;
        this.testCaseRepository = testCaseRepository;
        this.objectMapper = objectMapper;
        this.executor = evalTaskExecutor;
        this.evalExecutorService = evalExecutorService;
        this.agentFactory = agentFactory;
        this.evalLlmConfigRepository = evalLlmConfigRepository;
    }

    public static class TaskStatus {
        /** PENDING / RUNNING / COMPLETED / FAILED / TIMED_OUT */
        public String status;
        public String taskId;
        public String reportId;
        public String error;
        public long createdAt;
        public long submittedAt;
        public long completedAt;
        public int totalCases;
        public int completedCases;
        /** 任务级超时秒数（默认 300 = 5 分钟） */
        public int timeoutSeconds;

        /** 底层 Future，用于 cancel(true) 中断执行线程 */
        public Future<?> future;

        public TaskStatus(String taskId) {
            this.taskId = taskId;
            this.status = "PENDING";
            this.createdAt = System.currentTimeMillis();
            this.submittedAt = 0;
            this.totalCases = 0;
            this.completedCases = 0;
            this.timeoutSeconds = DEFAULT_TIMEOUT_SECONDS;
        }
    }

    /**
     * 提交评测任务（使用默认超时 5 分钟）。
     */
    public String submitTask(List<TestCase> testCases, List<String> metrics, String agentType) {
        return submitTask(testCases, metrics, agentType, DEFAULT_TIMEOUT_SECONDS, null, null, null, null, null);
    }

    public String submitTask(List<TestCase> testCases, List<String> metrics, String agentType, int timeoutSeconds) {
        return submitTask(testCases, metrics, agentType, timeoutSeconds, null, null, null, null, null);
    }

    /**
     * 提交评测任务并指定超时秒数、分组名称和三维分组维度。
     * @param timeoutSeconds 任务级超时；每个用例评测在 Evaluator 内部另有 30 秒 per-case 超时兜底。
     * @param group 报告所属分组名称（可为 null），用于报告历史按分组过滤。
     * @param project 三维分组：项目（可为 null）
     * @param module 三维分组：模块（可为 null）
     * @param function 三维分组：功能（可为 null）
     * @param evalConfigId 评测 LLM 配置 ID（可为 null）
     */
    public String submitTask(List<TestCase> testCases, List<String> metrics,
                             String agentType, int timeoutSeconds, String group,
                             String project, String module, String function) {
        return submitTask(testCases, metrics, agentType, timeoutSeconds, group, project, module, function, null);
    }

    public String submitTask(List<TestCase> testCases, List<String> metrics,
                             String agentType, int timeoutSeconds, String group,
                             String project, String module, String function,
                             String evalConfigId) {
        String taskId = "task_" + System.currentTimeMillis();
        TaskStatus status = new TaskStatus(taskId);
        status.totalCases = testCases.size();
        status.timeoutSeconds = timeoutSeconds;
        tasks.put(taskId, status);
        taskOrder.addLast(taskId);
        evictOldTasks();

        Callable<Void> callable = () -> {
            status.status = "RUNNING";
            status.submittedAt = System.currentTimeMillis();
            try {
                Agent agent = buildAgent(agentType);

                Evaluator.Builder builder = Evaluator.builder();
                if (evalConfigId != null && !evalConfigId.isEmpty()) {
                    EvalLlmConfig llmConfig = evalLlmConfigRepository.findById(evalConfigId).orElse(null);
                    if (llmConfig != null) {
                        builder.evalLlmConfig(llmConfig);
                    }
                }
                for (String metric : metrics) {
                    builder.metrics(metric);
                }
                Evaluator evaluator = builder.executorService(evalExecutorService).build();

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
                if (group != null && !group.trim().isEmpty()) {
                    reportData.put("group", group.trim());
                }
                if (project != null && !project.trim().isEmpty()) {
                    reportData.put("project", project.trim());
                }
                if (module != null && !module.trim().isEmpty()) {
                    reportData.put("module", module.trim());
                }
                if (function != null && !function.trim().isEmpty()) {
                    reportData.put("function", function.trim());
                }

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
            return null;
        };

        FutureTask<Void> futureTask = new FutureTask<>(callable);
        status.future = futureTask;
        executor.execute(futureTask);

        return taskId;
    }

    /**
     * 后台定时扫描：检测 RUNNING 任务是否超过 timeoutSeconds，超时则 cancel(true) 中断。
     * 扫描间隔 5 秒，由 Spring @Scheduled 驱动。
     */
    @Scheduled(fixedRate = 5000)
    public void checkTimeouts() {
        long now = System.currentTimeMillis();
        for (Map.Entry<String, TaskStatus> entry : tasks.entrySet()) {
            TaskStatus s = entry.getValue();
            if (!"RUNNING".equals(s.status)) continue;
            if (s.future == null) continue;
            if (s.submittedAt <= 0) continue;

            long elapsedMs = now - s.submittedAt;
            if (elapsedMs > s.timeoutSeconds * 1000L) {
                log.warn("Task {} has been running for {} ms (limit: {} s), cancelling...",
                        s.taskId, elapsedMs, s.timeoutSeconds);
                boolean mayInterrupt = s.future.cancel(true);
                s.status = "TIMED_OUT";
                s.error = "任务超时被系统强制中断（" + s.timeoutSeconds + " 秒）";
                s.completedAt = System.currentTimeMillis();
                log.info("Task {} cancel({}) = {}",
                        s.taskId, mayInterrupt, s.future.isCancelled());
            }
        }
    }

    private Agent buildAgent(String agentType) {
        return agentFactory.createAgent(agentType);
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

            // Look up readable test case name from repository (for PDF/UI display)
            String testCaseName = testCaseRepository
                    .findTestCaseById(ev.getTestCaseId())
                    .flatMap(e -> Optional.ofNullable(e.getName()))
                    .orElse(null);
            if (testCaseName != null && !testCaseName.isBlank()) {
                m.put("testCaseName", testCaseName);
            }

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
            TaskStatus removed = tasks.remove(oldest);
            // 如果该任务正在运行，也尝试取消
            if (removed != null && removed.future != null && !removed.future.isDone()) {
                removed.future.cancel(true);
            }
        }
    }

    public TaskStatus getStatus(String taskId) {
        return tasks.get(taskId);
    }

    public List<TaskStatus> getAllStatuses() {
        return new ArrayList<>(tasks.values());
    }
}
