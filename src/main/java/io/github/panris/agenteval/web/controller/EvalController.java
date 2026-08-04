package io.github.panris.agenteval.web.controller;

import io.github.panris.agenteval.Agent;
import io.github.panris.agenteval.Evaluator;
import io.github.panris.agenteval.TestCase;
import io.github.panris.agenteval.model.AgentConfig;
import io.github.panris.agenteval.model.EvalLlmConfig;
import io.github.panris.agenteval.repository.AgentConfigRepository;
import io.github.panris.agenteval.repository.EvalLlmConfigRepository;
import io.github.panris.agenteval.agent.AgentFactory;
import io.github.panris.agenteval.service.AsyncEvalService;
import io.github.panris.agenteval.service.EvalCaseService;
import io.github.panris.agenteval.service.EvalDimensionService;
import io.github.panris.agenteval.service.ReportService;
import io.github.panris.agenteval.web.Constants;
import io.github.panris.agenteval.web.dto.ApiResponse;
import io.github.panris.agenteval.web.dto.EvalRequest;
import io.swagger.v3.oas.annotations.Operation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ExecutorService;

/**
 * 评测执行 Controller：从原 EvalController 拆分出来，
 * 负责评测的发起（同步/异步）、首页/管理页路由。
 * 报告管理 → ReportController，异步任务查询 → TaskController。
 */
@Controller
public class EvalController {

    private static final Logger log = LoggerFactory.getLogger(EvalController.class);

    private final EvalCaseService evalCaseService;
    private final EvalLlmConfigRepository evalLlmConfigRepository;
    private final AgentConfigRepository agentConfigRepository;
    private final AsyncEvalService asyncEvalService;
    private final ReportService reportService;
    private final AgentFactory agentFactory;
    private final EvalDimensionService evalDimensionService;
    private final ExecutorService executorService;

    public EvalController(
            EvalCaseService evalCaseService,
            EvalLlmConfigRepository evalLlmConfigRepository,
            AgentConfigRepository agentConfigRepository,
            AsyncEvalService asyncEvalService,
            ReportService reportService,
            AgentFactory agentFactory,
            EvalDimensionService evalDimensionService,
            @org.springframework.beans.factory.annotation.Qualifier("evalExecutorService") ExecutorService executorService) {
        this.evalCaseService = evalCaseService;
        this.evalLlmConfigRepository = evalLlmConfigRepository;
        this.agentConfigRepository = agentConfigRepository;
        this.asyncEvalService = asyncEvalService;
        this.reportService = reportService;
        this.agentFactory = agentFactory;
        this.evalDimensionService = evalDimensionService;
        this.executorService = executorService;
    }

    // ─── Page routes ───────────────────────────────────────────────────────────

    @GetMapping("/")
    public String index(Model model) {
        model.addAttribute("testCases", List.of());
        return "index";
    }

    @GetMapping("/manage")
    public String manage() {
        return "manage";
    }

    // ─── Sync evaluation ──────────────────────────────────────────────────────

    @Operation(summary = "执行同步评测，返回完整报告")
    @PostMapping("/api/evaluate")
    @ResponseBody
    public Map<String, Object> evaluate(@RequestBody EvalRequest request) {
        return doEvaluate(request, null, null, null, null);
    }

    @Operation(summary = "按用例 ID 列表执行同步评测")
    @PostMapping("/api/evaluate/cases")
    @ResponseBody
    public Map<String, Object> evaluateByCaseIds(@RequestBody EvalRequest request) {
        if (request == null || request.getCaseIds() == null) {
            return ApiResponse.error("请提供 caseIds 列表");
        }
        return doEvaluate(request, null, null, null, null);
    }

    @Operation(summary = "按三维维度执行同步评测")
    @PostMapping("/api/evaluate/dimensions")
    @ResponseBody
    public Map<String, Object> evaluateByDimensions(@RequestBody EvalRequest request) {
        return doEvaluate(request, request.getGroup(),
                request.getProject(), request.getModule(), request.getFunction());
    }

    // ─── Async evaluation ─────────────────────────────────────────────────────

    @Operation(summary = "提交异步评测任务，返回任务 ID")
    @PostMapping("/api/evaluate/async")
    @ResponseBody
    public Map<String, Object> evaluateAsync(@RequestBody EvalRequest request) {
        Map<String, Object> validation = validateMetrics(request.getMetrics());
        if (validation != null) return validation;

        EvalCaseService.CaseResolution resolution = resolveCases(request);
        if (resolution.isError()) {
            return ApiResponse.error(resolution.errorMessage());
        }

        String taskId = asyncEvalService.submitTask(
                resolution.testCases(),
                request.getMetrics(),
                request.getAgentType(),
                300,
                request.getGroup(),
                request.getProject(),
                request.getModule(),
                request.getFunction(),
                request.getEvalConfigId(),
                request.getAgentConfigId());
        return ApiResponse.success(Map.of(
                "taskId", taskId,
                "message", "评测任务已提交，请通过 GET /api/tasks/" + taskId + " 查询进度"
        ));
    }

    // ─── Core evaluation logic ────────────────────────────────────────────────

    /**
     * Full param version used by evaluate (inline cases).
     */
    private Map<String, Object> doEvaluate(
            EvalRequest request,
            String group,
            String project,
            String module,
            String function) {
        if (request == null) {
            return ApiResponse.error("请求体不能为空");
        }
        Map<String, Object> validation = validateMetrics(request.getMetrics());
        if (validation != null) return validation;

        return doEvaluate(request, group, project, module, function,
                null, null, null, null, null,
                null, null, null, null, null);
    }

    /**
     * All-params version called by evaluate / evaluateByCaseIds / evaluateByDimensions.
     */
    private Map<String, Object> doEvaluate(
            EvalRequest request,
            String group,
            String project,
            String module,
            String function,
            List<String> metrics,
            String agentType,
            Map<String, Object> agentConfig,
            String agentConfigId,
            String evalConfigId,
            List<TestCase> testCases,
            String agentTypeOverride,
            Map<String, Object> agentConfigOverride,
            String agentConfigIdOverride,
            String evalConfigIdOverride) {

        List<String> m = metrics != null ? metrics : (request != null ? request.getMetrics() : null);
        String at = agentTypeOverride != null ? agentTypeOverride
                : (agentType != null ? agentType : (request != null ? request.getAgentType() : null));
        Map<String, Object> ac = agentConfigOverride != null ? agentConfigOverride
                : (agentConfig != null ? agentConfig : (request != null ? request.getAgentConfig() : null));
        String acid = agentConfigIdOverride != null ? agentConfigIdOverride
                : (agentConfigId != null ? agentConfigId : (request != null ? request.getAgentConfigId() : null));
        String eid = evalConfigIdOverride != null ? evalConfigIdOverride
                : (evalConfigId != null ? evalConfigId : (request != null ? request.getEvalConfigId() : null));
        String grp = group != null ? group : (request != null ? request.getGroup() : null);
        String prj = project != null ? project : (request != null ? request.getProject() : null);
        String mod = module != null ? module : (request != null ? request.getModule() : null);
        String fnc = function != null ? function : (request != null ? request.getFunction() : null);

        // Resolve cases if not provided
        EvalCaseService.CaseResolution resolution = resolveCases(
                testCases != null ? null : request,
                prj, mod, fnc);
        if (resolution == null || resolution.isError()) {
            return ApiResponse.error(resolution != null ? resolution.errorMessage() : "无法解析测试用例");
        }
        List<TestCase> cases = testCases != null ? testCases : resolution.testCases();

        Map<String, Object> validation = validateMetrics(m);
        if (validation != null) return validation;

        // Create agent
        Agent agent;
        try {
            if (acid != null && !acid.isEmpty()) {
                AgentConfig config = agentConfigRepository.findById(acid).orElse(null);
                if (config == null) {
                    return ApiResponse.error("Agent 配置不存在: " + acid);
                }
                agent = agentFactory.createAgent(config);
                log.info("Created agent from config: {}", config.getName());
            } else {
                if ("custom".equals(at) || "http".equals(at)) {
                    if (ac == null || ac.isEmpty()) {
                        return ApiResponse.error("使用自定义/HTTP Agent 时必须提供 agentConfig 或选择已配置的 Agent");
                    }
                    if (!ac.containsKey("endpoint")) {
                        return ApiResponse.error("自定义/HTTP Agent 配置缺少 endpoint 参数");
                    }
                }
                agent = agentFactory.createAgent(at, ac != null ? ac : Map.of());
                log.info("Created agent by type: {}", at);
            }
        } catch (IllegalArgumentException e) {
            log.error("Failed to create agent: {}", e.getMessage());
            return ApiResponse.error("Agent 配置错误: " + e.getMessage());
        }

        // Build evaluator
        Evaluator.Builder builder = Evaluator.builder();
        EvalLlmConfig llmConfig = null;

        if (eid != null && !eid.isEmpty()) {
            llmConfig = evalLlmConfigRepository.findById(eid).orElse(null);
            if (llmConfig != null) {
                log.info("Using explicit LLM eval config: {}", llmConfig.getName());
            }
        }
        if (llmConfig == null && prj != null) {
            llmConfig = evalDimensionService.resolveConfig(prj, mod, fnc);
            log.info("Resolved eval config from dimensions: project={}, module={}, function={}", prj, mod, fnc);
        }
        if (llmConfig != null) {
            builder.evalLlmConfig(llmConfig);
        }
        for (String metric : m) {
            builder.metrics(metric);
        }
        Evaluator evaluator = builder.executorService(executorService).build();

        // Run evaluation
        io.github.panris.agenteval.EvaluationReport evalReport = evaluator.evaluate(agent, cases);

        // Save to history
        String reportId = "report_" + UUID.randomUUID().toString().substring(0, 8);
        Map<String, Object> reportData = new LinkedHashMap<>();
        reportData.put("summary", evalReport.getSummary());
        reportData.put("evaluations", asyncEvalService.serializeEvaluations(evalReport.getEvaluations(), cases));
        reportData.put("totalTestCases", evalReport.getTotalTestCases());
        reportData.put("passedTestCases", evalReport.getPassedTestCases());
        reportData.put("failedTestCases", evalReport.getFailedTestCases());
        reportData.put("executionTimeMs", evalReport.getExecutionTimeMs());
        reportData.put("timestamp", System.currentTimeMillis());
        putDimensions(reportData, grp, prj, mod, fnc);
        reportService.saveReport(reportId, reportData);

        // Return result
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("success", true);
        result.put("reportId", reportId);
        result.put("summary", evalReport.getSummary());
        result.put("totalTestCases", evalReport.getTotalTestCases());
        result.put("passedTestCases", evalReport.getPassedTestCases());
        result.put("failedTestCases", evalReport.getFailedTestCases());
        result.put("executionTimeMs", evalReport.getExecutionTimeMs());
        result.put("evaluations", asyncEvalService.serializeEvaluations(evalReport.getEvaluations(), cases));
        putDimensions(result, grp, prj, mod, fnc);
        return result;
    }

    private Map<String, Object> validateMetrics(List<String> metrics) {
        if (metrics == null || metrics.isEmpty()) {
            return ApiResponse.error("评测指标不能为空");
        }
        for (String metric : metrics) {
            if (metric == null || metric.trim().isEmpty()) {
                return ApiResponse.error("评测指标名称不能为空");
            }
        }
        return null;
    }

    private EvalCaseService.CaseResolution resolveCases(EvalRequest request) {
        if (request == null) {
            return new EvalCaseService.CaseResolution("评测请求格式错误");
        }
        // Try cases (inline DTOs) first
        if (request.getCases() != null && !request.getCases().isEmpty()) {
            return evalCaseService.resolveFromDtos(request.getCases());
        }
        // Then caseIds
        if (request.getCaseIds() != null && !request.getCaseIds().isEmpty()) {
            return evalCaseService.resolveFromCaseIds(request.getCaseIds());
        }
        // Then dimensions
        if (request.getProject() != null || request.getModule() != null || request.getFunction() != null) {
            return evalCaseService.resolveFromDimensions(
                    request.getProject(), request.getModule(), request.getFunction());
        }
        return new EvalCaseService.CaseResolution("请提供测试用例（cases / caseIds / project+module+function 三选一）");
    }

    private EvalCaseService.CaseResolution resolveCases(EvalRequest request,
                                                       String project,
                                                       String module,
                                                       String function) {
        if (request != null) return resolveCases(request);
        // Dimensional resolution used by evaluateByCaseIds / evaluateByDimensions
        if (project != null || module != null || function != null) {
            return evalCaseService.resolveFromDimensions(project, module, function);
        }
        return new EvalCaseService.CaseResolution("请提供测试用例（cases / caseIds / project+module+function 三选一）");
    }

    private void putDimensions(Map<String, Object> map,
                              String group, String project, String module, String function) {
        if (group != null && !group.trim().isEmpty())   map.put("group",   group.trim());
        if (project != null && !project.trim().isEmpty()) map.put("project",  project.trim());
        if (module != null && !module.trim().isEmpty())  map.put("module",   module.trim());
        if (function != null && !function.trim().isEmpty()) map.put("function", function.trim());
    }
}
