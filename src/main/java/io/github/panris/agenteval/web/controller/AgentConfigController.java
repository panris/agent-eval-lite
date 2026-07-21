package io.github.panris.agenteval.web.controller;

import io.github.panris.agenteval.Agent;
import io.github.panris.agenteval.agent.AgentFactory;
import io.github.panris.agenteval.agent.AgentTemplates;
import io.github.panris.agenteval.model.AgentConfig;
import io.github.panris.agenteval.repository.AgentConfigRepository;
import io.github.panris.agenteval.web.dto.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Controller for managing Agent configurations.
 */
@RestController
@RequestMapping("/api/agents")
@Tag(name = "Agent Configuration", description = "Agent配置管理接口")
public class AgentConfigController {

    private static final Logger logger = LoggerFactory.getLogger(AgentConfigController.class);

    private final AgentConfigRepository repository;
    private final AgentFactory agentFactory;

    public AgentConfigController(AgentConfigRepository repository, AgentFactory agentFactory) {
        this.repository = repository;
        this.agentFactory = agentFactory;
    }

    // ============ List & Get ============

    @GetMapping
    @Operation(summary = "获取所有 Agent 配置", description = "返回所有已保存的 Agent 配置列表")
    public ResponseEntity<Map<String, Object>> getAllAgents() {
        List<AgentConfig> agents = repository.findAll();
        return ResponseEntity.ok(Map.of(
                "success", true,
                "agents", agents,
                "total", agents.size()
        ));
    }

    @GetMapping("/{id}")
    @Operation(summary = "根据ID获取 Agent 配置")
    public ResponseEntity<Map<String, Object>> getAgentById(@PathVariable String id) {
        return repository.findById(id)
                .map(config -> ResponseEntity.ok(Map.of("success", true, "agent", config)))
                .orElse(ResponseEntity.status(404).body(ApiResponse.error("Agent配置不存在")));
    }

    // ============ Templates ============

    @GetMapping("/templates")
    @Operation(summary = "获取预设模板", description = "返回可用的 Agent 配置模板（OpenAI、Claude、自定义等）")
    public ResponseEntity<Map<String, Object>> getTemplates() {
        List<AgentConfig> templates = AgentTemplates.getTemplates();
        return ResponseEntity.ok(Map.of(
                "success", true,
                "templates", templates
        ));
    }

    @GetMapping("/templates/{type}")
    @Operation(summary = "获取指定类型的模板", description = "返回指定类型的 Agent 配置模板")
    public ResponseEntity<Map<String, Object>> getTemplateByType(@PathVariable String type) {
        AgentConfig template = AgentTemplates.getTemplateByType(type);
        return ResponseEntity.ok(Map.of(
                "success", true,
                "template", template
        ));
    }

    // ============ Create & Update ============

    @PostMapping
    @Operation(summary = "创建 Agent 配置")
    public ResponseEntity<Map<String, Object>> createAgent(@RequestBody AgentConfig config) {
        // Validate required fields
        if (config.getName() == null || config.getName().trim().isEmpty()) {
            return ResponseEntity.badRequest().body(ApiResponse.error("Agent名称不能为空"));
        }
        if (config.getType() == null || config.getType().trim().isEmpty()) {
            return ResponseEntity.badRequest().body(ApiResponse.error("Agent类型不能为空"));
        }
        if (config.getEndpoint() == null || config.getEndpoint().trim().isEmpty()) {
            return ResponseEntity.badRequest().body(ApiResponse.error("Agent端点不能为空"));
        }

        // Set defaults
        if (config.getTimeout() <= 0) {
            config.setTimeout(30000);
        }

        AgentConfig saved = repository.save(config);
        logger.info("Created agent config: {} ({})", saved.getName(), saved.getId());

        return ResponseEntity.ok(Map.of(
                "success", true,
                "agent", saved,
                "message", "Agent配置创建成功"
        ));
    }

    private static final Set<String> VALID_TEMPLATE_TYPES = Set.of("openai", "claude", "custom", "http");

    @PostMapping("/from-template/{type}")
    @Operation(summary = "从模板创建 Agent 配置", description = "基于预设模板创建新的 Agent 配置")
    public ResponseEntity<Map<String, Object>> createFromTemplate(
            @PathVariable String type,
            @RequestBody(required = false) Map<String, Object> overrides) {

        if (!VALID_TEMPLATE_TYPES.contains(type.toLowerCase())) {
            return ResponseEntity.badRequest().body(ApiResponse.error("未知的模板类型: " + type));
        }
        AgentConfig template = AgentTemplates.getTemplateByType(type);

        // Apply overrides
        if (overrides != null) {
            if (overrides.containsKey("name")) {
                template.setName((String) overrides.get("name"));
            }
            if (overrides.containsKey("description")) {
                template.setDescription((String) overrides.get("description"));
            }
            if (overrides.containsKey("endpoint")) {
                template.setEndpoint((String) overrides.get("endpoint"));
            }
            if (overrides.containsKey("config") && template.getConfig() != null) {
                @SuppressWarnings("unchecked")
                Map<String, Object> configOverrides = (Map<String, Object>) overrides.get("config");
                template.getConfig().putAll(configOverrides);
            }
        }

        // Reset ID to create new
        template.setId(null);

        AgentConfig saved = repository.save(template);
        logger.info("Created agent from template {}: {} ({})", type, saved.getName(), saved.getId());

        return ResponseEntity.ok(Map.of(
                "success", true,
                "agent", saved,
                "message", "Agent配置创建成功"
        ));
    }

    @PutMapping("/{id}")
    @Operation(summary = "更新 Agent 配置")
    public ResponseEntity<Map<String, Object>> updateAgent(
            @PathVariable String id,
            @RequestBody AgentConfig config) {

        if (!repository.existsById(id)) {
            return ResponseEntity.status(404).body(ApiResponse.error("Agent配置不存在"));
        }

        config.setId(id);
        AgentConfig saved = repository.save(config);
        logger.info("Updated agent config: {} ({})", saved.getName(), saved.getId());

        return ResponseEntity.ok(Map.of(
                "success", true,
                "agent", saved,
                "message", "Agent配置更新成功"
        ));
    }

    // ============ Delete ============

    @DeleteMapping("/{id}")
    @Operation(summary = "删除 Agent 配置")
    public ResponseEntity<Map<String, Object>> deleteAgent(@PathVariable String id) {
        if (repository.deleteById(id)) {
            logger.info("Deleted agent config: {}", id);
            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "message", "Agent配置删除成功"
            ));
        } else {
            return ResponseEntity.status(404).body(ApiResponse.error("Agent配置不存在"));
        }
    }

    // ============ Test ============

    @PostMapping("/{id}/test")
    @Operation(summary = "测试 Agent 配置", description = "发送测试请求验证 Agent 配置是否正确")
    public ResponseEntity<Map<String, Object>> testAgent(
            @PathVariable String id,
            @RequestBody Map<String, String> body) {

        return repository.findById(id)
                .map(config -> {
                    String testInput = body.getOrDefault("input", "测试输入");
                    try {
                        Agent agent = agentFactory.createAgent(config);
                        long startTime = System.currentTimeMillis();
                        String response = agent.execute(testInput);
                        long responseTime = System.currentTimeMillis() - startTime;

                        boolean success = !response.startsWith("ERROR");
                        Map<String, Object> result = Map.of(
                                "success", success,
                                "input", testInput,
                                "output", response,
                                "responseTimeMs", responseTime,
                                "message", success ? "测试成功" : "测试失败：" + response
                        );
                        return ResponseEntity.ok(result);
                    } catch (Exception e) {
                        logger.error("Failed to test agent config {}: {}", id, e.getMessage(), e);
                        Map<String, Object> result = Map.of(
                                "success", false,
                                "input", testInput,
                                "error", e.getMessage(),
                                "message", "测试失败：" + e.getMessage()
                        );
                        return ResponseEntity.ok(result);
                    }
                })
                .orElseGet(() -> ResponseEntity.status(404).body(ApiResponse.error("Agent配置不存在")));
    }

    /**
     * 测试未经保存的配置（从表单直接测试）
     */
    @PostMapping("/test-config")
    @Operation(summary = "测试配置（不保存）", description = "测试表单中未经保存的 Agent 配置")
    public ResponseEntity<Map<String, Object>> testConfig(@RequestBody Map<String, Object> body) {
        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> configMap = (Map<String, Object>) body.get("config");
            String input = (String) body.getOrDefault("input", "测试输入");
            if (configMap == null) {
                return ResponseEntity.badRequest().body(ApiResponse.error("缺少 config 参数"));
            }

            AgentConfig config = mapToAgentConfig(configMap);
            Agent agent = agentFactory.createAgent(config);
            long startTime = System.currentTimeMillis();
            String response = agent.execute(input);
            long responseTime = System.currentTimeMillis() - startTime;

            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "input", input,
                    "output", response,
                    "responseTimeMs", responseTime
            ));
        } catch (Exception e) {
            logger.error("Failed to test config: {}", e.getMessage(), e);
            return ResponseEntity.ok(Map.of(
                    "success", false,
                    "error", e.getMessage(),
                    "message", e.getMessage()
            ));
        }
    }

    private AgentConfig mapToAgentConfig(Map<String, Object> m) {
        AgentConfig c = new AgentConfig();
        c.setName((String) m.getOrDefault("name", "临时测试"));
        c.setType((String) m.getOrDefault("type", "http"));
        c.setEndpoint((String) m.getOrDefault("endpoint", ""));
        c.setTimeout(m.get("timeout") instanceof Number n ? n.intValue() : 30000);

        @SuppressWarnings("unchecked")
        Map<String, String> headers = (Map<String, String>) m.get("headers");
        c.setHeaders(headers);

        @SuppressWarnings("unchecked")
        Map<String, Object> config = (Map<String, Object>) m.get("config");
        c.setConfig(config);

        @SuppressWarnings("unchecked")
        Map<String, Object> rm = (Map<String, Object>) m.get("responseMapping");
        if (rm != null) {
            AgentConfig.ResponseMapping responseMapping = new AgentConfig.ResponseMapping();
            responseMapping.setOutputPath((String) rm.get("outputPath"));
            responseMapping.setErrorPath((String) rm.get("errorPath"));
            responseMapping.setErrorMessagePath((String) rm.get("errorMessagePath"));
            c.setResponseMapping(responseMapping);
        }

        @SuppressWarnings("unchecked")
        Map<String, Object> reqMap = (Map<String, Object>) m.get("requestMapping");
        if (reqMap != null) {
            AgentConfig.RequestMapping requestMapping = new AgentConfig.RequestMapping();
            requestMapping.setTemplate((String) reqMap.get("template"));
            c.setRequestMapping(requestMapping);
        }

        return c;
    }
}
