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
    private final TestCaseRepository testCaseRepository;
    private final com.fasterxml.jackson.databind.ObjectMapper objectMapper = new com.fasterxml.jackson.databind.ObjectMapper();

    public EvalController(TestCaseRepository testCaseRepository) {
        this.testCaseRepository = testCaseRepository;
        loadReportHistory();
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
        // Create test cases
        List<TestCase> testCases = new ArrayList<>();
        for (TestCaseDto dto : request.getTestCases()) {
            testCases.add(new TestCase(dto.getInput(), dto.getExpected()));
        }

        return runEvaluation(testCases, request.getMetrics(), request.getAgentType());
    }

    /**
     * Evaluate by specific test case IDs.
     */
    @PostMapping("/api/evaluate/cases")
    @ResponseBody
    public Map<String, Object> evaluateByCaseIds(@RequestBody EvaluateByCaseIdsRequest request) {
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
            reports.add(Map.of(
                "id", id,
                "summary", report.get("summary"),
                "totalTestCases", report.get("totalTestCases"),
                "passedTestCases", report.get("passedTestCases"),
                "executionTimeMs", report.get("executionTimeMs"),
                "timestamp", report.get("timestamp")
            ));
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
