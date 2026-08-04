package io.github.panris.agenteval.web.dto;

import io.github.panris.agenteval.service.EvalCaseService;
import java.util.List;
import java.util.Map;

/**
 * 同步评测请求 DTO：从 EvalController 抽出来作为独立类型，
 * 供 evaluate / evaluateByCaseIds / evaluateByDimensions / evaluateAsync 复用。
 */
public class EvalRequest {

    private List<? extends EvalCaseService.TestCaseDtoLike> cases;
    private List<String> caseIds;
    private String project;
    private String module;
    private String function;
    private List<String> metrics;
    private String agentType;
    private Map<String, Object> agentConfig;
    private String agentConfigId;
    private String evalConfigId;
    private String group;

    // ─── Inline test cases ────────────────────────────────────────────────────

    /** Primary name */
    public List<? extends EvalCaseService.TestCaseDtoLike> getCases() { return cases; }
    public void setCases(List<? extends EvalCaseService.TestCaseDtoLike> cases) { this.cases = cases; }

    /** Alias for backward compatibility */
    public List<? extends EvalCaseService.TestCaseDtoLike> getTestCases() { return cases; }
    public void setTestCases(List<? extends EvalCaseService.TestCaseDtoLike> cases) { this.cases = cases; }

    // ─── By-case-ID lookup ────────────────────────────────────────────────────

    public List<String> getCaseIds() { return caseIds; }
    public void setCaseIds(List<String> caseIds) { this.caseIds = caseIds; }

    // ─── Three-dimensional filtering ─────────────────────────────────────────

    public String getProject() { return project; }
    public void setProject(String project) { this.project = project; }

    public String getModule() { return module; }
    public void setModule(String module) { this.module = module; }

    public String getFunction() { return function; }
    public void setFunction(String function) { this.function = function; }

    // ─── Agent / eval config ──────────────────────────────────────────────────

    public List<String> getMetrics() { return metrics; }
    public void setMetrics(List<String> metrics) { this.metrics = metrics; }

    public String getAgentType() { return agentType; }
    public void setAgentType(String agentType) { this.agentType = agentType; }

    public Map<String, Object> getAgentConfig() { return agentConfig; }
    public void setAgentConfig(Map<String, Object> agentConfig) { this.agentConfig = agentConfig; }

    public String getAgentConfigId() { return agentConfigId; }
    public void setAgentConfigId(String agentConfigId) { this.agentConfigId = agentConfigId; }

    public String getEvalConfigId() { return evalConfigId; }
    public void setEvalConfigId(String evalConfigId) { this.evalConfigId = evalConfigId; }

    public String getGroup() { return group; }
    public void setGroup(String group) { this.group = group; }
}
