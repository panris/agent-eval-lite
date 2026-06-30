package io.github.panris.agenteval.web.controller;

import io.github.panris.agenteval.Agent;
import io.github.panris.agenteval.EvaluationReport;
import io.github.panris.agenteval.Evaluator;
import io.github.panris.agenteval.TestCase;
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

    @GetMapping("/")
    public String index(Model model) {
        model.addAttribute("testCases", List.of(
            new TestCase("2+2=?", "4"),
            new TestCase("3*3=?", "9")
        ));
        model.addAttribute("metrics", List.of("correctness", "safety", "response_time"));
        return "index";
    }

    @PostMapping("/api/evaluate")
    @ResponseBody
    public Map<String, Object> evaluate(@RequestBody EvalRequest request) {
        // Create test cases
        List<TestCase> testCases = new ArrayList<>();
        for (TestCaseDto dto : request.getTestCases()) {
            testCases.add(new TestCase(dto.getInput(), dto.getExpected()));
        }

        // Create agent
        Agent agent = createAgent(request.getAgentType(), request.getAgentConfig());

        // Build evaluator
        Evaluator.Builder builder = Evaluator.builder();
        for (String metric : request.getMetrics()) {
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
