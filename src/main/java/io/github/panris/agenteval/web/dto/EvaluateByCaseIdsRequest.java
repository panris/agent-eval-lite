package io.github.panris.agenteval.web.dto;

import java.util.List;
import java.util.Map;

/**
 * 按用例 ID 列表执行评测的请求 DTO：从 EvalController 抽出来，
 * 供 evaluateByCaseIds 使用。
 */
public class EvaluateByCaseIdsRequest {

    private List<String> caseIds;
    private List<String> metrics;
    private String agentType;
    private Map<String, Object> agentConfig;
    private String agentConfigId;
    private String evalConfigId;

    public List<String> getCaseIds() { return caseIds; }
    public void setCaseIds(List<String> caseIds) { this.caseIds = caseIds; }

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
}
