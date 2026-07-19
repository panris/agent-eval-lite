package io.github.panris.agenteval.web.controller;

import io.github.panris.agenteval.Agent;
import io.github.panris.agenteval.Evaluation;
import io.github.panris.agenteval.EvaluationReport;
import io.github.panris.agenteval.Evaluator;
import io.github.panris.agenteval.TestCase;
import io.github.panris.agenteval.model.TestCaseEntity;
import io.github.panris.agenteval.repository.TestCaseRepository;
import io.github.panris.agenteval.repository.AgentConfigRepository;
import io.github.panris.agenteval.service.AsyncEvalService;
import io.github.panris.agenteval.service.ReportService;
import io.github.panris.agenteval.web.Constants;
import io.github.panris.agenteval.web.dto.ApiResponse;
import io.github.panris.agenteval.agent.AgentFactory;
import org.springframework.http.*;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import io.swagger.v3.oas.annotations.Operation;
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
import java.util.concurrent.ExecutorService;

@Controller
public class EvalController {
    private static final Logger log = LoggerFactory.getLogger(EvalController.class);

    private final ReportService reportService;
    private final com.fasterxml.jackson.databind.ObjectMapper objectMapper = new com.fasterxml.jackson.databind.ObjectMapper();
    private final TestCaseRepository testCaseRepository;
    private final AgentConfigRepository agentConfigRepository;
    private final AsyncEvalService asyncEvalService;
    private final AgentFactory agentFactory;
    private final ExecutorService executorService;

    public EvalController(TestCaseRepository testCaseRepository,
                           AgentConfigRepository agentConfigRepository,
                           AsyncEvalService asyncEvalService,
                           ReportService reportService,
                           AgentFactory agentFactory,
                           @org.springframework.beans.factory.annotation.Qualifier("evalExecutorService") ExecutorService executorService) {
        this.testCaseRepository = testCaseRepository;
        this.agentConfigRepository = agentConfigRepository;
        this.asyncEvalService = asyncEvalService;
        this.reportService = reportService;
        this.agentFactory = agentFactory;
        this.executorService = executorService;
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

    @Operation(summary = "执行同步评测，返回完整报告")
    @PostMapping("/api/evaluate")
    @ResponseBody
    public Map<String, Object> evaluate(@RequestBody EvalRequest request,
                                        @RequestParam(required = false) String agentConfigId) {
        if (request == null) {
            return ApiResponse.error("请求体不能为空");
        }
        if (request.getTestCases() == null || request.getTestCases().isEmpty()) {
            return ApiResponse.error("测试用例列表不能为空");
        }

        String agentType = request.getAgentType();
        if (agentType == null || agentType.trim().isEmpty()) {
            agentType = "demo";
        }

        Map<String, Object> metricsError = validateMetrics(request.getMetrics());
        if (metricsError != null) {
            return metricsError;
        }

        CaseResolution cr = resolveFromDtos(request.getTestCases());
        if (cr.hasError()) {
            return ApiResponse.error(cr.errorMessage());
        }

        return runEvaluation(cr.testCases(), request.getMetrics(), agentType, request.getAgentConfig(), agentConfigId, null, null, null, null);
    }

    /**
     * Evaluate by specific test case IDs.
     */
    @Operation(summary = "按用例 ID 列表执行同步评测")
    @PostMapping("/api/evaluate/cases")
    @ResponseBody
    public Map<String, Object> evaluateByCaseIds(@RequestBody EvaluateByCaseIdsRequest request) {
        if (request == null) {
            return ApiResponse.error("请求体不能为空");
        }
        if (request.getCaseIds() == null || request.getCaseIds().isEmpty()) {
            return ApiResponse.error("测试用例 ID 列表不能为空");
        }
        Map<String, Object> metricsError = validateMetrics(request.getMetrics());
        if (metricsError != null) {
            return metricsError;
        }
        CaseResolution cr = resolveFromCaseIds(request.getCaseIds());
        if (cr.hasError()) {
            return ApiResponse.error(cr.errorMessage());
        }
        return runEvaluation(cr.testCases(), request.getMetrics(), request.getAgentType(), null, null, null, null, null, null);
    }

    /**
     * Evaluate by group ID.
     */
    @Operation(summary = "按分组执行同步评测")
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
                        return new TestCase(entity.getId(), entity.getInput(), entity.getExpected(), null, null);
                    })
                    .toList();

                if (testCases.isEmpty()) {
                    return ApiResponse.error("该分组没有测试用例");
                }

                return runEvaluation(testCases, request.getMetrics(), request.getAgentType(), null, null, group.getName(), null, null, null);
            })
            .orElse(ApiResponse.error("分组不存在"));
    }

    /**
     * 按三维分组（项目/模块/功能）同步评测。任一维度为空表示不限制该维度。
     */
    @Operation(summary = "按三维维度执行同步评测")
    @PostMapping("/api/evaluate/dimensions")
    @ResponseBody
    public Map<String, Object> evaluateByDimensions(@RequestBody EvalRequest request) {
        Map<String, Object> metricsError = validateMetrics(request.getMetrics());
        if (metricsError != null) {
            return metricsError;
        }
        CaseResolution cr = resolveFromDimensions(request.getProject(), request.getModule(), request.getFunction());
        if (cr.hasError()) {
            return ApiResponse.error(cr.errorMessage());
        }
        String groupLabel = request.getFunction() != null ? request.getFunction()
            : request.getModule() != null ? request.getModule()
            : request.getProject();
        return runEvaluation(cr.testCases(), request.getMetrics(), request.getAgentType(), null, null, groupLabel,
            request.getProject(), request.getModule(), request.getFunction());
    }

    /**
     * Submit async batch evaluation task.
     */
    @Operation(summary = "提交异步评测任务，返回任务 ID")
    @PostMapping("/api/evaluate/async")
    @ResponseBody
    public Map<String, Object> evaluateAsync(@RequestBody EvalRequest request) {
        if (request == null) {
            return ApiResponse.error("请求不能为空");
        }
        Map<String, Object> metricsError = validateMetrics(request.getMetrics());
        if (metricsError != null) {
            return metricsError;
        }
        String agentType = request.getAgentType() != null ? request.getAgentType() : "demo";

        CaseResolution cr;
        if (request.getTestCases() != null && !request.getTestCases().isEmpty()) {
            cr = resolveFromDtos(request.getTestCases());
        } else if (request.getCaseIds() != null && !request.getCaseIds().isEmpty()) {
            cr = resolveFromCaseIds(request.getCaseIds());
        } else {
            cr = resolveFromDimensions(request.getProject(), request.getModule(), request.getFunction());
        }
        if (cr.hasError()) {
            return ApiResponse.error(cr.errorMessage());
        }
        String taskId = asyncEvalService.submitTask(cr.testCases(), request.getMetrics(), agentType,
                300, request.getGroup(), request.getProject(), request.getModule(), request.getFunction());
        return Map.of("success", true, "taskId", taskId, "status", "PENDING");
    }

    /**
     * Get async task status.
     */
    @Operation(summary = "查询异步评测任务状态")
    @GetMapping("/api/tasks/{taskId}")
    @ResponseBody
    public Map<String, Object> getTaskStatus(@PathVariable String taskId) {
        AsyncEvalService.TaskStatus status = asyncEvalService.getStatus(taskId);
        if (status == null) {
            return ApiResponse.error("任务不存在");
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
            "completedAt", status.completedAt,
            "timeoutSeconds", status.timeoutSeconds
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
            return ApiResponse.error("评测指标不能为空");
        }
        for (String metric : metrics) {
            if (metric == null || !Constants.VALID_METRICS.contains(metric)) {
                return ApiResponse.error("不支持的评测指标: " + metric);
            }
        }
        return null;
    }

    private Map<String, Object> runEvaluation(
        List<TestCase> testCases,
        List<String> metrics,
        String agentType,
        Map<String, Object> agentConfig,
        String agentConfigId,
        String group,
        String project,
        String module,
        String function
    ) {
        // Create agent from config ID or type
        Agent agent;
        if (agentConfigId != null && !agentConfigId.isEmpty()) {
            // Load from AgentConfigRepository
            io.github.panris.agenteval.model.AgentConfig config =
                agentConfigRepository.findById(agentConfigId).orElse(null);
            if (config == null) {
                return ApiResponse.error("Agent 配置不存在: " + agentConfigId);
            }
            agent = agentFactory.createAgent(config);
            log.info("Created agent from config: {}", config.getName());
        } else {
            agent = createAgent(agentType, agentConfig != null ? agentConfig : Map.of());
            log.info("Created agent by type: {}", agentType);
        }

        // Build evaluator
        Evaluator.Builder builder = Evaluator.builder();
        for (String metric : metrics) {
            builder.metrics(metric);
        }
        Evaluator evaluator = builder.executorService(executorService).build();

        // Run evaluation
        EvaluationReport report = evaluator.evaluate(agent, testCases);

        // Save to history (convert to serializable map)
        String reportId = "report_" + System.currentTimeMillis();
        Map<String, Object> reportData = new LinkedHashMap<>();
        reportData.put("summary", report.getSummary());
        reportData.put("evaluations", asyncEvalService.serializeEvaluations(report.getEvaluations(), testCases));
        reportData.put("totalTestCases", report.getTotalTestCases());
        reportData.put("passedTestCases", report.getPassedTestCases());
        reportData.put("failedTestCases", report.getFailedTestCases());
        reportData.put("executionTimeMs", report.getExecutionTimeMs());
        reportData.put("timestamp", System.currentTimeMillis());
        putDimensions(reportData, group, project, module, function);
        reportService.saveReport(reportId, reportData);


        // Return result
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("success", true);
        result.put("reportId", reportId);
        result.put("summary", report.getSummary());
        result.put("totalTestCases", report.getTotalTestCases());
        result.put("passedTestCases", report.getPassedTestCases());
        result.put("failedTestCases", report.getFailedTestCases());
        result.put("executionTimeMs", report.getExecutionTimeMs());
        putDimensions(result, group, project, module, function);
        return result;
    }

    /**
     * Helper: set dimension fields on a map, trimming and skipping null/blank values.
     */
    private void putDimensions(Map<String, Object> map, String group, String project, String module, String function) {
        if (group != null && !group.trim().isEmpty()) {
            map.put("group", group.trim());
        }
        if (project != null && !project.trim().isEmpty()) {
            map.put("project", project.trim());
        }
        if (module != null && !module.trim().isEmpty()) {
            map.put("module", module.trim());
        }
        if (function != null && !function.trim().isEmpty()) {
            map.put("function", function.trim());
        }
    }

    @GetMapping("/api/reports")
    @ResponseBody
    public Map<String, Object> getReports(
            @RequestParam(defaultValue = "desc") String sort,
            @RequestParam(required = false) Long since,
            @RequestParam(required = false) Long until,
            @RequestParam(required = false) String group,
            @RequestParam(required = false) String project,
            @RequestParam(required = false) String module,
            @RequestParam(required = false) String function,
            @RequestParam(required = false) Boolean favorite,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "time") String sortBy,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "false") boolean all) {
        if (size < 1) size = 20;
        if (size > 100) size = 100;
        return reportService.getAllReports(sort, since, until, group, project, module, function, favorite, status, keyword, sortBy, page, size, all);
    }

    @GetMapping("/api/reports/{id}")
    @ResponseBody
    public Map<String, Object> getReport(@PathVariable String id) {
        Map<String, Object> report = reportService.getReport(id);
        if (report == null) {
            return ApiResponse.error("报告不存在");
        }
        Map<String, Object> detail = new LinkedHashMap<>();
        detail.put("success", true);
        detail.put("reportId", id);
        detail.put("summary", report.get("summary"));
        detail.put("evaluations", report.get("evaluations"));
        detail.put("totalTestCases", report.get("totalTestCases"));
        detail.put("passedTestCases", report.get("passedTestCases"));
        detail.put("failedTestCases", report.get("failedTestCases"));
        detail.put("executionTimeMs", report.get("executionTimeMs"));
        detail.put("project", report.getOrDefault("project", null));
        detail.put("module", report.getOrDefault("module", null));
        detail.put("function", report.getOrDefault("function", null));
        detail.put("group", report.getOrDefault("group", null));
        return detail;
    }

    @DeleteMapping("/api/reports/{id}")
    @ResponseBody
    public Map<String, Object> deleteReport(@PathVariable String id) {
        return reportService.deleteReport(id);
    }

    @Operation(summary = "清空所有评测报告")
    @RequestMapping(value = "/api/reports", method = RequestMethod.DELETE)
    @ResponseBody
    public Map<String, Object> clearAllReports(@RequestBody(required = false) Map<String, String> body) {
        if (body != null && "clearAll".equals(body.get("action"))) {
            return reportService.clearAllReports();
        }
        return ApiResponse.error("无效的操作");
    }

    @GetMapping("/api/reports/{id}/export")
    public ResponseEntity<?> exportReport(
            @PathVariable String id,
            @RequestParam(defaultValue = "json") String format) {
        
        Map<String, Object> report = reportService.getReport(id);
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
        String csv = buildCsvMeta(report, reportId) + generateCsvFromMap(report);
        byte[] bytes = csv.getBytes(StandardCharsets.UTF_8);
        
        return ResponseEntity.ok()
            .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=report_" + reportId + ".csv")
            .contentType(MediaType.parseMediaType("text/csv"))
            .body(bytes);
    }
    
    /**
     * 报告级元信息（分组维度）以注释行形式置于 CSV 顶部，
     * 大多数表格解析器会忽略 # 开头的行。
     */
    private String buildCsvMeta(Map<String, Object> report, String reportId) {
        StringBuilder meta = new StringBuilder();
        meta.append("# 报告ID,").append(escapeCsv(reportId)).append("\n");
        Object group = report.get("group");
        if (group != null && !String.valueOf(group).trim().isEmpty()) {
            meta.append("# 分组,").append(escapeCsv(group)).append("\n");
        }
        Object project = report.get("project");
        if (project != null && !String.valueOf(project).trim().isEmpty()) {
            meta.append("# 项目,").append(escapeCsv(project)).append("\n");
        }
        Object module = report.get("module");
        if (module != null && !String.valueOf(module).trim().isEmpty()) {
            meta.append("# 模块,").append(escapeCsv(module)).append("\n");
        }
        Object func = report.get("function");
        if (func != null && !String.valueOf(func).trim().isEmpty()) {
            meta.append("# 功能,").append(escapeCsv(func)).append("\n");
        }
        Object ts = report.get("timestamp");
        if (ts instanceof Number) {
            meta.append("# 评测时间,").append(escapeCsv(new java.util.Date(((Number) ts).longValue()))).append("\n");
        }
        return meta.toString();
    }
    
    @SuppressWarnings("unchecked")
    private String generateCsvFromMap(Map<String, Object> report) {
        StringBuilder csv = new StringBuilder();
        csv.append("Test Case ID,Test Case Input,Passed,Overall Score");
        
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
                    csv.append(escapeCsv(m.get("testCaseId"))).append(",")
                        .append(escapeCsv(m.get("testCaseInput"))).append(",");
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
    @Operation(summary = "复制报告（生成新 ID）")
    @PostMapping("/api/reports/{id}/copy")
    @ResponseBody
    public Map<String, Object> copyReport(@PathVariable String id) {
        return reportService.copyReport(id);
    }

    // 收藏/取消收藏
    @Operation(summary = "切换报告收藏状态")
    @PostMapping("/api/reports/{id}/favorite")
    @ResponseBody
    public Map<String, Object> toggleFavorite(@PathVariable String id) {
        return reportService.toggleFavorite(id);
    }

    // 生成报告分享链接
    @Operation(summary = "生成分享链接")
    @PostMapping("/api/reports/{id}/share")
    @ResponseBody
    public Map<String, Object> shareReport(@PathVariable String id) {
        return reportService.createShareLink(id);
    }

    // 获取分享列表
    @Operation(summary = "获取收藏报告列表")
    @GetMapping("/api/reports/favorites")
    @ResponseBody
    public Map<String, Object> getFavorites() {
        return reportService.getFavorites();
    }

    // 更新报告标签
    @Operation(summary = "批量更新报告标签")
    @PutMapping("/api/reports/{id}/tags")
    @ResponseBody
    public Map<String, Object> updateTags(
            @PathVariable String id,
            @RequestBody Map<String, Object> body) {
        Object tags = body.get("tags");
        if (!(tags instanceof List)) {
            return ApiResponse.error("tags 必须是一个列表");
        }
        @SuppressWarnings("unchecked")
        List<String> tagList = (List<String>) tags;
        return reportService.updateTags(id, tagList);
    }

    // 更新报告备注
    @Operation(summary = "更新报告备注")
    @PutMapping("/api/reports/{id}/note")
    @ResponseBody
    public Map<String, Object> updateNote(
            @PathVariable String id,
            @RequestBody Map<String, Object> body) {
        return reportService.updateNote(id, (String) body.getOrDefault("note", ""));
    }

    // 对比报告
    @GetMapping("/api/reports/compare")
    @ResponseBody
    public Map<String, Object> compareReports(
            @RequestParam String ids,
            @RequestParam(required = false) String metric) {
        List<String> idList = java.util.Arrays.asList(ids.split(",")).stream()
            .map(String::trim)
            .filter(s -> !s.isEmpty())
            .distinct()
            .collect(java.util.stream.Collectors.toList());
        if (idList.size() < 2) {
            return ApiResponse.error("至少需要 2 个报告进行对比");
        }
        return reportService.compareReports(idList);
    }

    /**
     * Create agent using AgentFactory.
     */
    private Agent createAgent(String type, Map<String, Object> config) {
        return agentFactory.createAgent(type, config);
    }

    /** Result holder for test-case resolution. */
    private record CaseResolution(List<TestCase> testCases, String errorMessage) {
        CaseResolution(List<TestCase> testCases) { this(testCases, null); }
        CaseResolution(String errorMessage) { this(null, errorMessage); }
        boolean hasError() { return errorMessage != null; }
    }

    /** Resolve test cases from DTO list (used by sync evaluate + async). */
    private CaseResolution resolveFromDtos(List<TestCaseDto> dtos) {
        if (dtos.size() > Constants.MAX_CASES_PER_EVAL) {
            return new CaseResolution("测试用例数量不能超过 " + Constants.MAX_CASES_PER_EVAL + " 个");
        }
        for (int i = 0; i < dtos.size(); i++) {
            TestCaseDto dto = dtos.get(i);
            if (dto.getInput() == null || dto.getInput().trim().isEmpty()) {
                return new CaseResolution("第 " + (i + 1) + " 个测试用例的输入不能为空");
            }
            if (dto.getInput().length() > Constants.MAX_INPUT_LENGTH) {
                return new CaseResolution("第 " + (i + 1) + " 个测试用例的输入过长（最大 " + Constants.MAX_INPUT_LENGTH + " 字符）");
            }
            if (dto.getExpected() != null && dto.getExpected().length() > Constants.MAX_INPUT_LENGTH) {
                return new CaseResolution("第 " + (i + 1) + " 个测试用例的期望输出过长（最大 " + Constants.MAX_INPUT_LENGTH + " 字符）");
            }
        }
        List<TestCase> cases = dtos.stream()
            .map(dto -> new TestCase(dto.getInput(), dto.getExpected()))
            .toList();
        return new CaseResolution(cases);
    }

    /** Resolve test cases from repository by IDs (used by evaluateByCaseIds + async). */
    private CaseResolution resolveFromCaseIds(List<String> caseIds) {
        if (caseIds.size() > Constants.MAX_CASES_PER_EVAL) {
            return new CaseResolution("测试用例数量不能超过 " + Constants.MAX_CASES_PER_EVAL + " 个");
        }
        List<TestCase> cases = caseIds.stream()
            .map(id -> testCaseRepository.findTestCaseById(id))
            .filter(java.util.Optional::isPresent)
            .map(opt -> {
                TestCaseEntity e = opt.get();
                return new TestCase(e.getId(), e.getInput(), e.getExpected(), null, null);
            })
            .toList();
        if (cases.isEmpty()) {
            return new CaseResolution("未找到有效的测试用例");
        }
        return new CaseResolution(cases);
    }

    /** Resolve test cases by project/module/function dimensions (used by evaluateByDimensions + async). */
    private CaseResolution resolveFromDimensions(String project, String module, String function) {
        List<TestCaseEntity> byDims = testCaseRepository.findTestCasesByDimensions(project, module, function);
        if (byDims.isEmpty()) {
            return new CaseResolution("没有符合所选维度的测试用例");
        }
        if (byDims.size() > Constants.MAX_CASES_PER_EVAL) {
            return new CaseResolution("测试用例数量不能超过 " + Constants.MAX_CASES_PER_EVAL + " 个（当前 " + byDims.size() + "）");
        }
        List<TestCase> cases = byDims.stream()
            .map(e -> new TestCase(e.getId(), e.getInput(), e.getExpected(), null, null))
            .toList();
        return new CaseResolution(cases);
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
// TEST_MARKER_1234567890XYZ
