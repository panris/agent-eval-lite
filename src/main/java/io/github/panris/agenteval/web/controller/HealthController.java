package io.github.panris.agenteval.web.controller;

import io.github.panris.agenteval.repository.TestCaseRepository;
import io.github.panris.agenteval.service.ReportService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

@RestController
public class HealthController {

    private static final Instant START_TIME = Instant.now();
    private static final String VERSION = "0.1.0";

    private final TestCaseRepository testCaseRepository;
    private final ReportService reportService;

    public HealthController(TestCaseRepository testCaseRepository, ReportService reportService) {
        this.testCaseRepository = testCaseRepository;
        this.reportService = reportService;
    }

    @GetMapping("/api/health")
    public Map<String, Object> health() {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("status", "UP");
        result.put("timestamp", Instant.now().toString());
        result.put("service", "agent-eval-lite");
        result.put("version", VERSION);
        result.put("uptimeSeconds", Instant.now().getEpochSecond() - START_TIME.getEpochSecond());

        int testCaseCount = 0;
        int reportCount = 0;
        try {
            testCaseCount = testCaseRepository.countAllTestCases();
            reportCount = (int) reportService.getAllReports("desc", null, null, null, null, null, null, null, null, null, "time", 1, 1, false).get("total");
        } catch (Exception e) {
            // 健康检查时数据访问失败不影响主状态，仅返回 0
        }

        result.put("testCases", testCaseCount);
        result.put("reports", reportCount);
        return result;
    }
}
