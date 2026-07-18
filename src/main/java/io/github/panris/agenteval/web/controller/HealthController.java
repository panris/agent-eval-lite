package io.github.panris.agenteval.web.controller;

import io.github.panris.agenteval.repository.TestCaseRepository;
import io.github.panris.agenteval.repository.AgentConfigRepository;
import io.github.panris.agenteval.service.ReportService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import io.swagger.v3.oas.annotations.Operation;

@RestController
public class HealthController {

    private static final Instant START_TIME = Instant.now();
    private static final String VERSION = "0.1.0";

    private final TestCaseRepository testCaseRepository;
    private final ReportService reportService;
    private final AgentConfigRepository agentConfigRepository;

    public HealthController(TestCaseRepository testCaseRepository, 
                           ReportService reportService,
                           AgentConfigRepository agentConfigRepository) {
        this.testCaseRepository = testCaseRepository;
        this.reportService = reportService;
        this.agentConfigRepository = agentConfigRepository;
    }

    @Operation(summary = "健康检查接口")
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
        int groupCount = 0;
        int agentCount = 0;
        try {
            testCaseCount = testCaseRepository.countAllTestCases();
            reportCount = (int) reportService.getAllReports("desc", null, null, null, null, null, null, null, null, null, "time", 1, 1, false).get("total");
            groupCount = testCaseRepository.findAllGroups().size();
            agentCount = (int) agentConfigRepository.count();
        } catch (Exception e) {
            // 健康检查时数据访问失败不影响主状态，仅返回 0
        }

        result.put("testCases", testCaseCount);
        result.put("reports", reportCount);
        result.put("groups", groupCount);
        result.put("agents", agentCount);
        return result;
    }
}
