package io.github.panris.agenteval.web.controller;

import io.github.panris.agenteval.Agent;
import io.github.panris.agenteval.Evaluation;
import io.github.panris.agenteval.EvaluationReport;
import io.github.panris.agenteval.Evaluator;
import io.github.panris.agenteval.TestCase;
import io.github.panris.agenteval.config.AppConfig;
import io.github.panris.agenteval.model.TestCaseEntity;
import io.github.panris.agenteval.repository.TestCaseRepository;
import io.github.panris.agenteval.service.AsyncEvalService;
import org.springframework.http.*;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Controller
public class EvalController {
    private static final Logger log = LoggerFactory.getLogger(EvalController.class);

    private final Map<String, Map<String, Object>> reportHistory;
    private final Map<String, String> sharedReports;  // shareId -> reportId
    private final TestCaseRepository testCaseRepository;
    private final AsyncEvalService asyncEvalService;
    private final AppConfig appConfig;
    private final com.fasterxml.jackson.databind.ObjectMapper objectMapper = new com.fasterxml.jackson.databind.ObjectMapper();

    private static final List<String> VALID_METRICS =
            List.of("correctness", "safety", "response_time", "bleu", "rouge", "similarity");

    public EvalController(TestCaseRepository testCaseRepository,
                           Map<String, Map<String, Object>> reportHistory,
                           Map<String, String> sharedReports,
                           AsyncEvalService asyncEvalService,
                           AppConfig appConfig) {
        this.testCaseRepository = testCaseRepository;
        this.reportHistory = reportHistory;
        this.sharedReports = sharedReports;
        this.asyncEvalService = asyncEvalService;
        this.appConfig = appConfig;
        loadReportHistory();
        appConfig.loadSharedReports(sharedReports);
        // Clean up old reports if too many
        cleanupOldReports(100);  // Keep latest 100 reports
    }

    /**
     * Clean up old reports if count exceeds limit.
     */
    private void cleanupOldReports(int maxReports) {
        if (reportHistory.size() <= maxReports) return;
        
        log.info("Cleaning up old reports. Current count: {}", reportHistory.size());
        
        // Sort by timestamp and keep latest maxReports
        var sorted = reportHistory.entrySet().stream()
            .sorted((a, b) -> {
                Long tsA = getTimestamp(a.getValue());
                Long tsB = getTimestamp(b.getValue());
                return tsB.compareTo(tsA);  // Descending
            })
            .toList();
        
        // Collect IDs being removed so we can clean up sharedReports too
        var keptIds = sorted.stream().limit(maxReports).map(Map.Entry::getKey).collect(java.util.stream.Collectors.toSet());
        
        // Clear and reload latest maxReports
        reportHistory.clear();
        sorted.stream().limit(maxReports).forEach(e -> reportHistory.put(e.getKey(), e.getValue()));
        
        // Also remove stale entries from sharedReports (those pointing to deleted reports)
        sharedReports.entrySet().removeIf(entry -> !keptIds.contains(entry.getValue()));
        
        saveReportHistory();
        log.info("Cleanup complete. Kept {} reports", reportHistory.size());
    }
    
    private Long getTimestamp(Map<String, Object> report) {
        Object timestamp = report.get("timestamp");
        if (timestamp instanceof Number) return ((Number) timestamp).longValue();
        return 0L;
    }

    @PostConstruct
    public void loadReportHistory() {
        Path dataFile = Paths.get("data/reports.json");
        if (Files.exists(dataFile)) {
            try {
                String content = Files.readString(dataFile);
                Map<String, Map<String, Object>> loaded = objectMapper.readValue(content,
                    objectMapper.getTypeFactory().constructMapType(LinkedHashMap.class, String.class, Map.class));
                reportHistory.putAll(loaded);
                log.info("Loaded {} historical reports", reportHistory.size());
            } catch (Exception e) {
                log.error("Failed to load report history: {}", e.getMessage(), e);
            }
        }
    }

    private void saveReportHistory() {
        try {
            Path dataFile = Paths.get("data/reports.json");
            Files.createDirectories(dataFile.getParent());
            String json = objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(reportHistory);
            Files.writeString(dataFile, json);
        } catch (Exception e) {
            log.error("Failed to save report history: {}", e.getMessage(), e);
        }
    }

    @GetMapping("/")
    public String index(Model model) {
        model.addAttribute("testCases", List.of(
            new TestCase("2+2=?", "4"),
            new TestCase("3*3=?", "9")
        ));
        model.addAttribute("metrics", List.of("correctness", "safety", "response_time", "bleu", "rouge", "similarity"));
        return "index";
    }

    @GetMapping("/manage")
    public String manage() {
        return "manage";
    }

    @PostMapping("/api/evaluate")
    @ResponseBody
    public Map<String, Object> evaluate(@RequestBody EvalRequest request) {
        // Validate request
        if (request == null) {
            return Map.of("success", false, "error", "请求体不能为空");
        }
        
        if (request.getTestCases() == null || request.getTestCases().isEmpty()) {
            return Map.of("success", false, "error", "测试用例列表不能为空");
        }
        
        if (request.getTestCases().size() > 100) {
            return Map.of("success", false, "error", "测试用例数量不能超过 100 个");
        }
        
        // Validate each test case
        for (int i = 0; i < request.getTestCases().size(); i++) {
            TestCaseDto dto = request.getTestCases().get(i);
            if (dto.getInput() == null || dto.getInput().trim().isEmpty()) {
                return Map.of("success", false, "error", "第 " + (i + 1) + " 个测试用例的输入不能为空");
            }
            if (dto.getInput().length() > 10000) {
                return Map.of("success", false, "error", "第 " + (i + 1) + " 个测试用例的输入过长（最大 10000 字符）");
            }
            if (dto.getExpected() != null && dto.getExpected().length() > 10000) {
                return Map.of("success", false, "error", "第 " + (i + 1) + " 个测试用例的期望输出过长（最大 10000 字符）");
            }
        }
        
        // Validate agent type
        String agentType = request.getAgentType();
        if (agentType == null || agentType.trim().isEmpty()) {
            agentType = "demo";
        }
        
        // Validate metrics
        Map<String, Object> metricsError = validateMetrics(request.getMetrics());
        if (metricsError != null) {
            return metricsError;
        }
        
        // Create test cases
        List<TestCase> testCases = new ArrayList<>();
        for (TestCaseDto dto : request.getTestCases()) {
            testCases.add(new TestCase(dto.getInput(), dto.getExpected()));
        }

        return runEvaluation(testCases, request.getMetrics(), agentType);
    }

    /**
     * Evaluate by specific test case IDs.
     */
    @PostMapping("/api/evaluate/cases")
    @ResponseBody
    public Map<String, Object> evaluateByCaseIds(@RequestBody EvaluateByCaseIdsRequest request) {
        // Validate request
        if (request == null) {
            return Map.of("success", false, "error", "请求体不能为空");
        }
        
        if (request.getCaseIds() == null || request.getCaseIds().isEmpty()) {
            return Map.of("success", false, "error", "测试用例 ID 列表不能为空");
        }
        
        if (request.getCaseIds().size() > 100) {
            return Map.of("success", false, "error", "测试用例数量不能超过 100 个");
        }
        
        Map<String, Object> metricsError = validateMetrics(request.getMetrics());
        if (metricsError != null) {
            return metricsError;
        }
        
        // Get test cases by IDs
        List<TestCase> testCases = request.getCaseIds().stream()
            .map(caseId -> testCaseRepository.findTestCaseById(caseId))
            .filter(opt -> opt.isPresent())
            .map(opt -> {
                TestCaseEntity entity = opt.get();
                return new TestCase(entity.getInput(), entity.getExpected());
            })
            .toList();

        if (testCases.isEmpty()) {
            return Map.of(
                "success", false,
                "error", "No valid test cases found"
            );
        }

        // Run evaluation
        return runEvaluation(testCases, request.getMetrics(), request.getAgentType());
    }

    /**
     * Evaluate by group ID.
     */
    @PostMapping("/api/evaluate/group/{groupId}")
    @ResponseBody
    public Map<String, Object> evaluateByGroup(
        @PathVariable String groupId,
        @RequestBody EvaluateByGroupRequest request
    ) {
        return testCaseRepository.findGroupById(groupId)
            .map(group -> {
                Map<String, Object> metricsError = validateMetrics(request.getMetrics());
                if (metricsError != null) {
                    return metricsError;
                }
                // Get test cases from group
                List<TestCase> testCases = group.getTestCaseIds().stream()
                    .map(caseId -> testCaseRepository.findTestCaseById(caseId))
                    .filter(opt -> opt.isPresent())
                    .map(opt -> {
                        TestCaseEntity entity = opt.get();
                        return new TestCase(entity.getInput(), entity.getExpected());
                    })
                    .toList();

                if (testCases.isEmpty()) {
                    return Map.<String, Object>of(
                        "success", false,
                        "error", "Group has no test cases"
                    );
                }

                return runEvaluation(testCases, request.getMetrics(), request.getAgentType());
            })
            .orElse(Map.of(
                "success", false,
                "error", "Group not found"
            ));
    }

    /**
     * Submit async batch evaluation task.
     */
    @PostMapping("/api/evaluate/async")
    @ResponseBody
    public Map<String, Object> evaluateAsync(@RequestBody EvalRequest request) {
        if (request == null || request.getTestCases() == null || request.getTestCases().isEmpty()) {
            return Map.of("success", false, "error", "测试用例列表不能为空");
        }
        if (request.getTestCases().size() > 100) {
            return Map.of("success", false, "error", "测试用例数量不能超过 100 个");
        }
        Map<String, Object> metricsError = validateMetrics(request.getMetrics());
        if (metricsError != null) {
            return metricsError;
        }
        List<TestCase> testCases = new ArrayList<>();
        for (TestCaseDto dto : request.getTestCases()) {
            testCases.add(new TestCase(dto.getInput(), dto.getExpected()));
        }
        String agentType = request.getAgentType() != null ? request.getAgentType() : "demo";
        String taskId = asyncEvalService.submitTask(testCases, request.getMetrics(), agentType);
        return Map.of("success", true, "taskId", taskId, "status", "PENDING");
    }

    /**
     * Get async task status.
     */
    @GetMapping("/api/tasks/{taskId}")
    @ResponseBody
    public Map<String, Object> getTaskStatus(@PathVariable String taskId) {
        AsyncEvalService.TaskStatus status = asyncEvalService.getStatus(taskId);
        if (status == null) {
            return Map.of("success", false, "error", "Task not found");
        }
        return Map.of(
            "success", true,
            "taskId", status.taskId,
            "status", status.status,
            "reportId", status.reportId != null ? status.reportId : "",
            "error", status.error != null ? status.error : "",
            "totalCases", status.totalCases,
            "completedCases", status.completedCases,
            "createdAt", status.createdAt,
            "completedAt", status.completedAt
        );
    }

    /**
     * List all async tasks.
     */
    @GetMapping("/api/tasks")
    @ResponseBody
    public List<Map<String, Object>> listTasks() {
        return asyncEvalService.getAllStatuses().stream()
            .sorted((a, b) -> Long.compare(b.createdAt, a.createdAt))
            .limit(50)
            .map(s -> Map.<String, Object>of(
                "taskId", s.taskId,
                "status", s.status,
                "reportId", s.reportId != null ? s.reportId : "",
                "totalCases", s.totalCases,
                "completedCases", s.completedCases,
                "createdAt", s.createdAt,
                "completedAt", s.completedAt
            ))
            .toList();
    }

    /**
     * Validate the requested metrics list. Returns an error response map if invalid,
     * or null if validation passes. Guards against null metrics (NPE in Evaluator)
     * and unknown metric names.
     */
    private Map<String, Object> validateMetrics(List<String> metrics) {
        if (metrics == null || metrics.isEmpty()) {
            return Map.of("success", false, "error", "评测指标不能为空");
        }
        for (String metric : metrics) {
            if (metric == null || !VALID_METRICS.contains(metric)) {
                return Map.of("success", false, "error", "不支持的评测指标: " + metric);
            }
        }
        return null;
    }

    private Map<String, Object> runEvaluation(
        List<TestCase> testCases,
        List<String> metrics,
        String agentType
    ) {
        // Create agent
        Agent agent = createAgent(agentType, Map.of());

        // Build evaluator
        Evaluator.Builder builder = Evaluator.builder();
        for (String metric : metrics) {
            builder.metrics(metric);
        }
        Evaluator evaluator = builder.build();

        // Run evaluation
        EvaluationReport report = evaluator.evaluate(agent, testCases);

        // Save to history (convert to serializable map)
        String reportId = "report_" + System.currentTimeMillis();
        Map<String, Object> reportData = Map.of(
            "summary", report.getSummary(),
            "evaluations", report.getEvaluations(),
            "totalTestCases", report.getTotalTestCases(),
            "passedTestCases", report.getPassedTestCases(),
            "failedTestCases", report.getFailedTestCases(),
            "executionTimeMs", report.getExecutionTimeMs(),
            "timestamp", System.currentTimeMillis()
        );
        reportHistory.put(reportId, reportData);
        saveReportHistory();

        // Return result
        return Map.of(
            "success", true,
            "reportId", reportId,
            "summary", report.getSummary(),
            "totalTestCases", report.getTotalTestCases(),
            "passedTestCases", report.getPassedTestCases(),
            "failedTestCases", report.getFailedTestCases(),
            "executionTimeMs", report.getExecutionTimeMs()
        );
    }

    @GetMapping("/api/reports")
    @ResponseBody
    public List<Map<String, Object>> getReports(@RequestParam(defaultValue = "desc") String sort) {
        List<Map<String, Object>> reports = new ArrayList<>();
        reportHistory.forEach((id, report) -> {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("id", id);
            item.put("summary", report.get("summary"));
            item.put("totalTestCases", report.get("totalTestCases"));
            item.put("passedTestCases", report.get("passedTestCases"));
            item.put("executionTimeMs", report.get("executionTimeMs"));
            item.put("timestamp", report.get("timestamp"));
            item.put("favorite", report.getOrDefault("favorite", false));
            item.put("tags", report.getOrDefault("tags", List.of()));
            item.put("note", report.getOrDefault("note", ""));
            reports.add(item);
        });
        
        // Sort by timestamp: desc = newest first (default), asc = oldest first
        boolean descending = "desc".equalsIgnoreCase(sort);
        reports.sort((a, b) -> {
            Object tsA = a.get("timestamp");
            Object tsB = b.get("timestamp");
            long tA = tsA instanceof Number ? ((Number) tsA).longValue() : 0L;
            long tB = tsB instanceof Number ? ((Number) tsB).longValue() : 0L;
            return descending ? Long.compare(tB, tA) : Long.compare(tA, tB);
        });
        
        return reports;
    }

    @GetMapping("/api/reports/{id}")
    @ResponseBody
    public Map<String, Object> getReport(@PathVariable String id) {
        Map<String, Object> report = reportHistory.get(id);
        if (report == null) {
            return Map.of("success", false, "error", "Report not found");
        }
        return Map.of(
            "success", true,
            "reportId", id,
            "summary", report.get("summary"),
            "evaluations", report.get("evaluations"),
            "totalTestCases", report.get("totalTestCases"),
            "passedTestCases", report.get("passedTestCases"),
            "failedTestCases", report.get("failedTestCases"),
            "executionTimeMs", report.get("executionTimeMs")
        );
    }

    @DeleteMapping("/api/reports/{id}")
    @ResponseBody
    public Map<String, Object> deleteReport(@PathVariable String id) {
        if (reportHistory.remove(id) != null) {
            saveReportHistory();
            return Map.of("success", true, "message", "Report deleted");
        }
        return Map.of("success", false, "error", "Report not found");
    }

    @RequestMapping(value = "/api/reports", method = RequestMethod.DELETE)
    @ResponseBody
    public Map<String, Object> clearAllReports(@RequestBody(required = false) Map<String, String> body) {
        if (body != null && "clearAll".equals(body.get("action"))) {
            reportHistory.clear();
            saveReportHistory();
            return Map.of("success", true, "message", "All reports cleared");
        }
        return Map.of("success", false, "error", "Invalid action");
    }

    @GetMapping("/api/reports/{id}/export")
    public ResponseEntity<?> exportReport(
            @PathVariable String id,
            @RequestParam(defaultValue = "json") String format) {
        
        Map<String, Object> report = reportHistory.get(id);
        if (report == null) {
            return ResponseEntity.notFound().build();
        }
        
        if ("csv".equalsIgnoreCase(format)) {
            return exportAsCsv(report, id);
        } else {
            return exportAsJson(report, id);
        }
    }
    
    private ResponseEntity<?> exportAsJson(Map<String, Object> report, String reportId) {
        try {
            Map<String, Object> json = Map.of(
                "reportId", reportId,
                "summary", report.get("summary"),
                "evaluations", report.get("evaluations"),
                "exportTime", Instant.now().toString()
            );
            
            String jsonStr = objectMapper.writeValueAsString(json);
            byte[] bytes = jsonStr.getBytes(StandardCharsets.UTF_8);
            
            return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=report_" + reportId + ".json")
                .contentType(MediaType.APPLICATION_JSON)
                .body(bytes);
        } catch (Exception e) {
            log.error("Failed to export report JSON: {}", e.getMessage(), e);
            return ResponseEntity.internalServerError().build();
        }
    }
    
    private ResponseEntity<?> exportAsCsv(Map<String, Object> report, String reportId) {
        String csv = generateCsvFromMap(report);
        byte[] bytes = csv.getBytes(StandardCharsets.UTF_8);
        
        return ResponseEntity.ok()
            .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=report_" + reportId + ".csv")
            .contentType(MediaType.parseMediaType("text/csv"))
            .body(bytes);
    }
    
    @SuppressWarnings("unchecked")
    private String generateCsvFromMap(Map<String, Object> report) {
        StringBuilder csv = new StringBuilder();
        csv.append("Test Case ID,Passed,Overall Score");
        
        Object evaluationsObj = report.get("evaluations");
        if (evaluationsObj instanceof List<?> list && !list.isEmpty()) {
            Object first = list.get(0);
            if (first instanceof Map) {
                Map<String, Object> firstMap = (Map<String, Object>) first;
                Object results = firstMap.get("scorerResults");
                if (results instanceof Map) {
                    for (String name : ((Map<String, Object>) results).keySet()) {
                        csv.append(",").append(name).append(" Score");
                        csv.append(",").append(name).append(" Passed");
                        csv.append(",").append(name).append(" Rationale");
                    }
                }
            }
        }
        csv.append("\n");
        
        if (evaluationsObj instanceof List<?> list) {
            for (Object item : list) {
                if (item instanceof Map) {
                    Map<String, Object> m = (Map<String, Object>) item;
                    csv.append(escapeCsv(m.get("testCaseId"))).append(",");
                    csv.append(escapeCsv(m.get("passed"))).append(",");
                    csv.append(escapeCsv(m.get("overallScore")));
                    
                    Object results = m.get("scorerResults");
                    if (results instanceof Map) {
                        for (Object sr : ((Map<String, Object>) results).values()) {
                            if (sr instanceof Map) {
                                Map<String, Object> srMap = (Map<String, Object>) sr;
                                csv.append(",").append(escapeCsv(srMap.get("score")));
                                csv.append(",").append(escapeCsv(srMap.get("passed")));
                                csv.append(",").append(escapeCsv(srMap.get("rationale")));
                            }
                        }
                    }
                    csv.append("\n");
                }
            }
        }
        
        return csv.toString();
    }

    /**
     * Escape a value for safe CSV field inclusion.
     * Prevents CSV formula injection (leading =, +, -, @) and handles commas/quotes/newlines.
     */
    private String escapeCsv(Object value) {
        if (value == null) return "";
        String s = value.toString();
        if (s.isEmpty()) return "";
        // Prefix formula-like starts to prevent CSV injection
        if (s.startsWith("=") || s.startsWith("+") || s.startsWith("-") || s.startsWith("@")) {
            s = "'" + s;
        }
        boolean needsQuotes = s.contains(",") || s.contains("\"") || s.contains("\n") || s.contains("\r");
        if (needsQuotes) {
            return "\"" + s.replace("\"", "\"\"") + "\"";
        }
        return s;
    }

    // 复制报告
    @PostMapping("/api/reports/{id}/copy")
    @ResponseBody
    public Map<String, Object> copyReport(@PathVariable String id) {
        Map<String, Object> original = reportHistory.get(id);
        if (original == null) {
            return Map.of("success", false, "error", "Report not found");
        }
        String newId = "report_" + System.currentTimeMillis();
        reportHistory.put(newId, new LinkedHashMap<>(original));
        saveReportHistory();
        return Map.of("success", true, "newId", newId, "message", "Report copied");
    }

    // 收藏/取消收藏
    @PostMapping("/api/reports/{id}/favorite")
    @ResponseBody
    public Map<String, Object> toggleFavorite(@PathVariable String id) {
        Map<String, Object> report = reportHistory.get(id);
        if (report == null) {
            return Map.of("success", false, "error", "Report not found");
        }
        boolean current = (boolean) report.getOrDefault("favorite", false);
        report.put("favorite", !current);
        saveReportHistory();
        return Map.of("success", true, "favorite", !current);
    }

    // 生成报告分享链接
    @PostMapping("/api/reports/{id}/share")
    @ResponseBody
    public Map<String, Object> shareReport(@PathVariable String id) {
        Map<String, Object> report = reportHistory.get(id);
        if (report == null) {
            return Map.of("success", false, "error", "Report not found");
        }
        // 生成简短的分享 ID (8位)
        String shareId = java.util.UUID.randomUUID().toString().substring(0, 8);
        // 存储分享映射
        sharedReports.put(shareId, id);
        appConfig.saveSharedReports(sharedReports);  // 持久化
        return Map.of("success", true, "shareId", shareId, "url", "/share/" + shareId);
    }

    // 获取分享列表
    @GetMapping("/api/reports/favorites")
    @ResponseBody
    public Map<String, Object> getFavorites() {
        Map<String, Map<String, Object>> favorites = new LinkedHashMap<>();
        reportHistory.forEach((reportId, report) -> {
            if ((boolean) report.getOrDefault("favorite", false)) {
                favorites.put(reportId, report);
            }
        });
        return Map.of("success", true, "favorites", favorites);
    }

    // 更新报告标签
    @PutMapping("/api/reports/{id}/tags")
    @ResponseBody
    public Map<String, Object> updateTags(
            @PathVariable String id,
            @RequestBody Map<String, Object> body) {
        Map<String, Object> report = reportHistory.get(id);
        if (report == null) {
            return Map.of("success", false, "error", "Report not found");
        }
        Object tags = body.get("tags");
        if (tags instanceof List) {
            report.put("tags", tags);
        }
        saveReportHistory();
        return Map.of("success", true, "tags", report.get("tags"));
    }

    // 更新报告备注
    @PutMapping("/api/reports/{id}/note")
    @ResponseBody
    public Map<String, Object> updateNote(
            @PathVariable String id,
            @RequestBody Map<String, Object> body) {
        Map<String, Object> report = reportHistory.get(id);
        if (report == null) {
            return Map.of("success", false, "error", "Report not found");
        }
        report.put("note", body.getOrDefault("note", ""));
        saveReportHistory();
        return Map.of("success", true, "note", report.get("note"));
    }

    // 对比报告
    @GetMapping("/api/reports/compare")
    @ResponseBody
    public Map<String, Object> compareReports(
            @RequestParam String ids,
            @RequestParam(required = false) String metric) {
        String[] reportIds = ids.split(",");
        List<Map<String, Object>> reports = new ArrayList<>();
        for (String reportId : reportIds) {
            Map<String, Object> report = reportHistory.get(reportId.trim());
            if (report != null) {
                reports.add(report);
            }
        }
        
        if (reports.isEmpty()) {
            return Map.of("success", false, "error", "No valid reports found");
        }
        
        // 计算对比数据
        Map<String, Object> comparison = new LinkedHashMap<>();
        comparison.put("count", reports.size());
        comparison.put("reports", reports);
        
        // 计算各指标统计
        List<Double> scores = new ArrayList<>();
        List<Double> passRates = new ArrayList<>();
        List<Long> execTimes = new ArrayList<>();
        List<Integer> totalCases = new ArrayList<>();

        for (Map<String, Object> r : reports) {
            Object summaryObj = r.get("summary");
            if (summaryObj instanceof Map) {
                Map<?, ?> summary = (Map<?, ?>) summaryObj;

                Object scoreObj = summary.get("averageScore");
                if (scoreObj == null) scoreObj = summary.get("average_score");
                if (scoreObj instanceof Number) scores.add(((Number) scoreObj).doubleValue());

                Object prObj = summary.get("passRate");
                if (prObj == null) prObj = summary.get("pass_rate");
                if (prObj instanceof Number) passRates.add(((Number) prObj).doubleValue());

                Object tcObj = summary.get("totalTestCases");
                if (tcObj == null) tcObj = summary.get("total_test_cases");
                if (tcObj instanceof Number) totalCases.add(((Number) tcObj).intValue());
            }

            Object execObj = r.get("executionTimeMs");
            if (execObj instanceof Number) execTimes.add(((Number) execObj).longValue());
        }

        if (!scores.isEmpty()) {
            scores.sort(Double::compareTo);
            comparison.put("scoreStats", Map.of(
                "min", scores.get(0),
                "max", scores.get(scores.size() - 1),
                "avg", scores.stream().mapToDouble(Double::doubleValue).average().orElse(0)
            ));
        }

        if (!passRates.isEmpty()) {
            passRates.sort(Double::compareTo);
            comparison.put("passRateStats", Map.of(
                "min", passRates.get(0),
                "max", passRates.get(passRates.size() - 1),
                "avg", passRates.stream().mapToDouble(Double::doubleValue).average().orElse(0)
            ));
        }

        if (!execTimes.isEmpty()) {
            execTimes.sort(Long::compare);
            comparison.put("execTimeStats", Map.of(
                "min", execTimes.get(0),
                "max", execTimes.get(execTimes.size() - 1),
                "avg", execTimes.stream().mapToLong(Long::longValue).average().orElse(0)
            ));
        }

        if (!totalCases.isEmpty()) {
            comparison.put("totalCases", totalCases);
        }
        
        return Map.of("success", true, "comparison", comparison);
    }

    private Agent createAgent(String type, Map<String, Object> config) {
        // For demo, create a simple echo agent
        // In production, this would create real agents based on type
        return input -> {
            if (input.contains("+")) {
                String[] parts = input.split("\\+");
                if (parts.length == 2) {
                    try {
                        int a = Integer.parseInt(parts[0].trim().replaceAll("[^0-9]", ""));
                        int b = Integer.parseInt(parts[1].trim().replaceAll("[^0-9]", ""));
                        return String.valueOf(a + b);
                    } catch (Exception e) {
                        return "Calculation error";
                    }
                }
            }
            if (input.contains("*")) {
                String[] parts = input.split("\\*");
                if (parts.length == 2) {
                    try {
                        int a = Integer.parseInt(parts[0].trim().replaceAll("[^0-9]", ""));
                        int b = Integer.parseInt(parts[1].trim().replaceAll("[^0-9]", ""));
                        return String.valueOf(a * b);
                    } catch (Exception e) {
                        return "Calculation error";
                    }
                }
            }
            return "I'm a demo agent. You asked: " + input;
        };
    }
}

class EvaluateByCaseIdsRequest {
    private List<String> caseIds;
    private List<String> metrics;
    private String agentType;

    public List<String> getCaseIds() {
        return caseIds;
    }

    public void setCaseIds(List<String> caseIds) {
        this.caseIds = caseIds;
    }

    public List<String> getMetrics() {
        return metrics;
    }

    public void setMetrics(List<String> metrics) {
        this.metrics = metrics;
    }

    public String getAgentType() {
        return agentType;
    }

    public void setAgentType(String agentType) {
        this.agentType = agentType;
    }
}

class EvaluateByGroupRequest {
    private List<String> metrics;
    private String agentType;

    public List<String> getMetrics() {
        return metrics;
    }

    public void setMetrics(List<String> metrics) {
        this.metrics = metrics;
    }

    public String getAgentType() {
        return agentType;
    }

    public void setAgentType(String agentType) {
        this.agentType = agentType;
    }
}
