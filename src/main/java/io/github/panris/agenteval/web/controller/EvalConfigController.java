package io.github.panris.agenteval.web.controller;

import io.github.panris.agenteval.model.EvalDimensionConfig;
import io.github.panris.agenteval.model.EvalDimensionConfig.DimensionLevel;
import io.github.panris.agenteval.model.EvalModel;
import io.github.panris.agenteval.repository.EvalDimensionConfigRepository;
import io.github.panris.agenteval.repository.EvalModelRepository;
import io.github.panris.agenteval.web.dto.ApiResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.List;

@Controller
public class EvalConfigController {

    private final EvalModelRepository modelRepository;
    private final EvalDimensionConfigRepository configRepository;

    public EvalConfigController(EvalModelRepository modelRepository, EvalDimensionConfigRepository configRepository) {
        this.modelRepository = modelRepository;
        this.configRepository = configRepository;
    }

    @GetMapping("/eval-config")
    public String evalConfigPage() {
        return "eval-config";
    }

    @GetMapping("/api/eval-config/models")
    @ResponseBody
    public ResponseEntity<?> getAllModels() {
        return ResponseEntity.ok(modelRepository.findAll());
    }

    @GetMapping("/api/eval-config/models/{id}")
    @ResponseBody
    public ResponseEntity<?> getModel(@PathVariable String id) {
        return modelRepository.findById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping("/api/eval-config/models")
    @ResponseBody
    public ResponseEntity<?> createModel(@RequestBody EvalModel model) {
        model.setCreatedAt(Instant.now());
        model.setUpdatedAt(Instant.now());
        if (model.getIsDefault() == null) model.setIsDefault(false);
        
        if (model.getIsDefault()) {
            modelRepository.findByIsDefaultTrue().ifPresent(m -> {
                m.setIsDefault(false);
                m.setUpdatedAt(Instant.now());
                modelRepository.save(m);
            });
        }
        
        return ResponseEntity.ok(modelRepository.save(model));
    }

    @PutMapping("/api/eval-config/models/{id}")
    @ResponseBody
    public ResponseEntity<?> updateModel(@PathVariable String id, @RequestBody EvalModel model) {
        return modelRepository.findById(id)
                .map(existing -> {
                    existing.setName(model.getName());
                    existing.setProvider(model.getProvider());
                    existing.setBaseUrl(model.getBaseUrl());
                    existing.setApiKey(model.getApiKey());
                    existing.setTemperature(model.getTemperature());
                    existing.setMaxTokens(model.getMaxTokens());
                    existing.setTimeout(model.getTimeout());
                    existing.setDescription(model.getDescription());
                    
                    if (model.getIsDefault() != null) {
                        if (model.getIsDefault() && !existing.getIsDefault()) {
                            modelRepository.findByIsDefaultTrue().ifPresent(m -> {
                                m.setIsDefault(false);
                                m.setUpdatedAt(Instant.now());
                                modelRepository.save(m);
                            });
                            existing.setIsDefault(true);
                        } else if (!model.getIsDefault() && existing.getIsDefault()) {
                            existing.setIsDefault(false);
                        }
                    }
                    
                    existing.setUpdatedAt(Instant.now());
                    return ResponseEntity.ok(modelRepository.save(existing));
                })
                .orElse(ResponseEntity.notFound().build());
    }

    @DeleteMapping("/api/eval-config/models/{id}")
    @ResponseBody
    public ResponseEntity<?> deleteModel(@PathVariable String id) {
        if (modelRepository.existsById(id)) {
            modelRepository.deleteById(id);
            return ResponseEntity.ok(ApiResponse.success("message", "删除成功"));
        }
        return ResponseEntity.notFound().build();
    }

    @GetMapping("/api/eval-config/dimensions")
    @ResponseBody
    public ResponseEntity<?> getAllDimensionConfigs() {
        return ResponseEntity.ok(configRepository.findAll());
    }

    @GetMapping("/api/eval-config/dimensions/{id}")
    @ResponseBody
    public ResponseEntity<?> getDimensionConfig(@PathVariable String id) {
        return configRepository.findById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping("/api/eval-config/dimensions")
    @ResponseBody
    public ResponseEntity<?> createDimensionConfig(@RequestBody EvalDimensionConfig config) {
        validateDimensionConfig(config);
        config.setCreatedAt(Instant.now());
        config.setUpdatedAt(Instant.now());
        return ResponseEntity.ok(configRepository.save(config));
    }

    @PutMapping("/api/eval-config/dimensions/{id}")
    @ResponseBody
    public ResponseEntity<?> updateDimensionConfig(@PathVariable String id, @RequestBody EvalDimensionConfig config) {
        validateDimensionConfig(config);
        return configRepository.findById(id)
                .map(existing -> {
                    existing.setLevel(config.getLevel());
                    existing.setProject(config.getProject());
                    existing.setModule(config.getModule());
                    existing.setFunction(config.getFunction());
                    existing.setModelId(config.getModelId());
                    existing.setSystemPrompt(config.getSystemPrompt());
                    existing.setPassThreshold(config.getPassThreshold());
                    existing.setDescription(config.getDescription());
                    existing.setUpdatedAt(Instant.now());
                    return ResponseEntity.ok(configRepository.save(existing));
                })
                .orElse(ResponseEntity.notFound().build());
    }

    @DeleteMapping("/api/eval-config/dimensions/{id}")
    @ResponseBody
    public ResponseEntity<?> deleteDimensionConfig(@PathVariable String id) {
        if (configRepository.existsById(id)) {
            configRepository.deleteById(id);
            return ResponseEntity.ok(ApiResponse.success("message", "删除成功"));
        }
        return ResponseEntity.notFound().build();
    }

    private void validateDimensionConfig(EvalDimensionConfig config) {
        DimensionLevel level = config.getLevel();
        if (level == null) {
            throw new IllegalArgumentException("维度级别不能为空");
        }
        
        if (level == DimensionLevel.GLOBAL) {
            config.setProject(null);
            config.setModule(null);
            config.setFunction(null);
        } else if (level == DimensionLevel.PROJECT) {
            if (config.getProject() == null || config.getProject().isEmpty()) {
                throw new IllegalArgumentException("项目维度必须指定项目");
            }
            config.setModule(null);
            config.setFunction(null);
        } else if (level == DimensionLevel.MODULE) {
            if (config.getProject() == null || config.getProject().isEmpty()) {
                throw new IllegalArgumentException("模块维度必须指定项目");
            }
            if (config.getModule() == null || config.getModule().isEmpty()) {
                throw new IllegalArgumentException("模块维度必须指定模块");
            }
            config.setFunction(null);
        } else if (level == DimensionLevel.FUNCTION) {
            if (config.getProject() == null || config.getProject().isEmpty()) {
                throw new IllegalArgumentException("功能维度必须指定项目");
            }
            if (config.getModule() == null || config.getModule().isEmpty()) {
                throw new IllegalArgumentException("功能维度必须指定模块");
            }
            if (config.getFunction() == null || config.getFunction().isEmpty()) {
                throw new IllegalArgumentException("功能维度必须指定功能");
            }
        }
    }
}