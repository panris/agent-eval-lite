package io.github.panris.agenteval.web.controller;

import io.github.panris.agenteval.Agent;
import io.github.panris.agenteval.Evaluation;
import io.github.panris.agenteval.EvaluationReport;
import io.github.panris.agenteval.Evaluator;
import io.github.panris.agenteval.TestCase;
import io.github.panris.agenteval.model.TestCaseEntity;
import io.github.panris.agenteval.repository.TestCaseRepository;
import org.springframework.http.*;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import jakarta.annotation.PostConstruct;
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

    private final Map<String, Map<String, Object>> reportHistory = new ConcurrentHashMap<>();
    private final Map<String, String> sharedReports = new ConcurrentHashMap<>();  // shareId -> reportId
    private final TestCaseRepository testCaseRepository;
    private final com.fasterxml.jackson.databind.ObjectMapper objectMapper = new com.fasterxml.jackson.databind.ObjectMapper();

    public EvalController(TestCaseRepository testCaseRepository) {
        this.testCaseRepository = testCaseRepository;
        loadReportHistory();
        // Clean up old reports if too many
        cleanupOldReports(100);  // Keep latest 100 reports
    }

    /**
     * Clean up old reports if count exceeds limit.
     */
    private void cleanupOldReports(int maxReports) {
        if (reportHistory.size() <= maxReports) return;
        
        System.out.println("Cleaning up old reports. Current count: " + reportHistory.size());
        
        // Sort by timestamp and keep latest maxReports
        var sorted = reportHistory.entrySet().stream()
            .sorted((a, b) -> {
                Long tsA = getTimestamp(a.getValue());
                Long tsB = getTimestamp(b.getValue());
                return tsB.compareTo(tsA);  // Descending
            })
            .toList();
        
        // Clear and reload latest maxReports
        reportHistory.clear();
        sorted.stream().limit(maxReports).forEach(e -> reportHistory.put(e.getKey(), e.getValue()));
        
        saveReportHistory();
        System.out.println("Cleanup complete. Kept " + reportHistory.size() + " reports");
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
                System.out.println("Loaded " + reportHistory.size() + " historical reports");
            } catch (Exception e) {
                System.err.println("Failed to load report history: " + e.getMessage());
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
            System.err.println("Failed to save report history: " + e.getMessage());
        }
    }

    @GetMapping("/")
    public String index(Model model) {
        model.addAttribute("testCases", List.of(
            new TestCase("2+2=?", "4"),
            new TestCase("3*3=?", "9")
        ));
        model.addAttribute("metrics", List.of("correctness", "safety", "response_time"));
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
        
        if (request.getMetrics() == null || request.getMetrics().isEmpty()) {
            return Map.of("success", false, "error", "评测指标不能为空");
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
        List<String> validMetrics = List.of("correctness", "safety", "response_time");
        for (String metric : request.getMetrics()) {
            if (!validMetrics.contains(metric)) {
                return Map.of("success", false, "error", "不支持的评测指标: " + metric);
            }
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
    public List<Map<String, Object>> getReports() {
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
            
            String jsonStr = new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(json);
            byte[] bytes = jsonStr.getBytes(StandardCharsets.UTF_8);
            
            return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=report_" + reportId + ".json")
                .contentType(MediaType.APPLICATION_JSON)
                .body(bytes);
        } catch (Exception e) {
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
                    csv.append(m.get("testCaseId") != null ? m.get("testCaseId") : "").append(",");
                    csv.append(m.get("passed") != null ? m.get("passed") : "").append(",");
                    csv.append(m.get("overallScore") != null ? m.get("overallScore") : "");
                    
                    Object results = m.get("scorerResults");
                    if (results instanceof Map) {
                        for (Object sr : ((Map<String, Object>) results).values()) {
                            if (sr instanceof Map) {
                                Map<String, Object> srMap = (Map<String, Object>) sr;
                                csv.append(",").append(srMap.get("score") != null ? srMap.get("score") : "");
                                csv.append(",").append(srMap.get("passed") != null ? srMap.get("passed") : "");
                                csv.append(",\"").append(srMap.get("rationale") != null ? srMap.get("rationale").toString().replace("\"", "'") : "").append("\"");
                            }
                        }
                    }
                    csv.append("\n");
                }
            }
        }
        
        return csv.toString();
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
        if ("score".equals(metric) || metric == null) {
            List<Double> scores = new ArrayList<>();
            for (Map<String, Object> r : reports) {
                Object summaryObj = r.get("summary");
                if (summaryObj instanceof Map) {
                    Object scoreObj = ((Map<?, ?>) summaryObj).get("averageScore");
                    if (scoreObj instanceof Number) {
                        scores.add(((Number) scoreObj).doubleValue());
                    }
                }
            }
            if (!scores.isEmpty()) {
                scores.sort(Double::compareTo);
                comparison.put("scoreStats", Map.of(
                    "min", scores.get(0),
                    "max", scores.get(scores.size() - 1),
                    "avg", scores.stream().mapToDouble(Double::doubleValue).average().orElse(0)
                ));
            }
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

// 分享页面控制器
@Controller
class ShareController {
    private final Map<String, Map<String, Object>> reportHistory;
    private final Map<String, String> sharedReports;

    public ShareController(Map<String, Map<String, Object>> reportHistory,
                           Map<String, String> sharedReports) {
        this.reportHistory = reportHistory;
        this.sharedReports = sharedReports;
    }

    @GetMapping("/share/{shareId}")
    public String showSharedReport(@PathVariable String shareId, Model model) {
        String reportId = sharedReports.get(shareId);
        if (reportId == null) {
            return "redirect:/";
        }
        
        Map<String, Object> report = reportHistory.get(reportId);
        if (report == null) {
            return "redirect:/";
        }
        
        model.addAttribute("reportId", reportId);
        model.addAttribute("summary", report.get("summary"));
        model.addAttribute("timestamp", report.get("timestamp"));
        model.addAttribute("evaluations", report.get("evaluations"));
        model.addAttribute("totalTestCases", report.getOrDefault("totalTestCases", 0));
        model.addAttribute("passedTestCases", report.getOrDefault("passedTestCases", 0));
        model.addAttribute("failedTestCases", report.getOrDefault("failedTestCases", 0));
        
        return "share";
    }
}
