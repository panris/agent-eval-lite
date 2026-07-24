package io.github.panris.agenteval.web.controller;

import io.github.panris.agenteval.model.EvalLlmConfig;
import io.github.panris.agenteval.repository.EvalLlmConfigRepository;
import io.github.panris.agenteval.web.dto.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/eval-llm-configs")
@Tag(name = "评测模型配置")
public class EvalLlmConfigController {
    private static final Logger logger = LoggerFactory.getLogger(EvalLlmConfigController.class);
    private final EvalLlmConfigRepository repo;

    public EvalLlmConfigController(EvalLlmConfigRepository repo) { this.repo = repo; }

    @GetMapping
    @Operation(summary = "获取所有评测模型配置")
    public ResponseEntity<Map<String, Object>> getAll() {
        var configs = repo.findAll();
        return ResponseEntity.ok(Map.of("success", true, "configs", configs, "total", configs.size()));
    }

    @GetMapping("/{id}")
    @Operation(summary = "获取指定评测模型配置")
    public ResponseEntity<Map<String, Object>> getById(@PathVariable String id) {
        return repo.findById(id).map(c -> ResponseEntity.ok(Map.of("success", true, "config", c)))
            .orElse(ResponseEntity.status(404).body(ApiResponse.error("配置不存在")));
    }

    @PostMapping
    @Operation(summary = "创建评测模型配置")
    public ResponseEntity<Map<String, Object>> create(@RequestBody EvalLlmConfig config) {
        if (isBlank(config.getName())) return badReq("名称不能为空");
        if (isBlank(config.getBaseUrl())) return badReq("Base URL不能为空");
        if (isBlank(config.getModel())) return badReq("模型名不能为空");
        var saved = repo.save(config);
        logger.info("Created eval LLM config: {}", saved.getName());
        return ResponseEntity.ok(Map.of("success", true, "config", saved, "message", "创建成功"));
    }

    @PutMapping("/{id}")
    @Operation(summary = "更新评测模型配置")
    public ResponseEntity<Map<String, Object>> update(@PathVariable String id, @RequestBody EvalLlmConfig config) {
        if (!repo.existsById(id)) return ResponseEntity.status(404).body(ApiResponse.error("配置不存在"));
        config.setId(id);
        var saved = repo.save(config);
        return ResponseEntity.ok(Map.of("success", true, "config", saved, "message", "更新成功"));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "删除评测模型配置")
    public ResponseEntity<Map<String, Object>> delete(@PathVariable String id) {
        return repo.deleteById(id) ? ResponseEntity.ok(Map.of("success", true, "message", "删除成功"))
            : ResponseEntity.status(404).body(ApiResponse.error("配置不存在"));
    }

    @GetMapping("/presets")
    @Operation(summary = "获取预设模板")
    public ResponseEntity<Map<String, Object>> getPresets() {
        return ResponseEntity.ok(Map.of("success", true, "presets", List.of(
            Map.of("name","DashScope Qwen3.6-35B-A3B","baseUrl","https://dashscope.aliyuncs.com/compatible-mode/v1/chat/completions","model","qwen3.6-35b-a3b","apiKey","sk-xxx","temperature",0.1,"maxTokens",256,"passThreshold",0.7),
            Map.of("name","OpenAI GPT-4o-mini","baseUrl","https://api.openai.com/v1/chat/completions","model","gpt-4o-mini","apiKey","sk-xxx","temperature",0.1,"maxTokens",256,"passThreshold",0.7),
            Map.of("name","ThunderSoft意图模型","baseUrl","http://36.150.116.241:18088/v1/chat/completions","model","qwen3.6-35b-a3b","apiKey","novastack_2026","temperature",0.1,"maxTokens",256,"passThreshold",0.7)
        )));
    }

    private boolean isBlank(String s) { return s == null || s.trim().isEmpty(); }
    private ResponseEntity<Map<String, Object>> badReq(String msg) { return ResponseEntity.badRequest().body(ApiResponse.error(msg)); }
}