package io.github.panris.agenteval.web.controller;

import io.github.panris.agenteval.Agent;
import io.github.panris.agenteval.EvaluationReport;
import io.github.panris.agenteval.Evaluator;
import io.github.panris.agenteval.TestCase;
import io.github.panris.agenteval.model.TestCaseEntity;
import io.github.panris.agenteval.repository.TestCaseRepository;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Controller
public class EvalController {

    private final Map<String, EvaluationReport> reportHistory = new ConcurrentHashMap<>();
    private final TestCaseRepository testCaseRepository;

    public EvalController(TestCaseRepository testCaseRepository) {
        this.testCaseRepository = testCaseRepository;
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

        // Save to history
        String reportId = "report_" + System.currentTimeMillis();
        reportHistory.put(reportId, report);

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
                "summary", report.getSummary(),
                "totalTestCases", report.getTotalTestCases(),
                "passedTestCases", report.getPassedTestCases(),
                "executionTimeMs", report.getExecutionTimeMs()
            ));
        });
        return reports;
    }

    @GetMapping("/api/reports/{id}")
    @ResponseBody
    public Map<String, Object> getReport(@PathVariable String id) {
        EvaluationReport report = reportHistory.get(id);
        if (report == null) {
            return Map.of("success", false, "error", "Report not found");
        }
        return Map.of(
            "success", true,
            "summary", report.getSummary(),
            "evaluations", report.getEvaluations()
        );
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
