package io.github.panris.agenteval.service;

import io.github.panris.agenteval.model.EvalDimensionConfig;
import io.github.panris.agenteval.model.EvalDimensionConfig.DimensionLevel;
import io.github.panris.agenteval.model.EvalLlmConfig;
import io.github.panris.agenteval.model.EvalModel;
import io.github.panris.agenteval.repository.EvalDimensionConfigRepository;
import io.github.panris.agenteval.repository.EvalModelRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.Optional;

@Service
public class EvalDimensionService {

    private static final Logger log = LoggerFactory.getLogger(EvalDimensionService.class);

    private final EvalDimensionConfigRepository configRepository;
    private final EvalModelRepository modelRepository;

    public EvalDimensionService(EvalDimensionConfigRepository configRepository, EvalModelRepository modelRepository) {
        this.configRepository = configRepository;
        this.modelRepository = modelRepository;
    }

    public EvalLlmConfig resolveConfig(String project, String module, String function) {
        log.info("Resolving eval config for: project={}, module={}, function={}", project, module, function);
        
        EvalDimensionConfig config = findEffectiveConfig(project, module, function);
        EvalModel model = null;
        
        if (config != null && config.getModelId() != null) {
            model = modelRepository.findById(config.getModelId()).orElse(null);
        }
        
        if (model == null) {
            model = modelRepository.findByIsDefaultTrue().orElse(null);
        }
        
        return buildEvalLlmConfig(config, model);
    }

    private EvalDimensionConfig findEffectiveConfig(String project, String module, String function) {
        if (function != null && !function.trim().isEmpty() && 
            module != null && !module.trim().isEmpty() && 
            project != null && !project.trim().isEmpty()) {
            Optional<EvalDimensionConfig> funcConfig = configRepository.findFunctionConfig(project, module, function);
            if (funcConfig.isPresent()) {
                log.info("Found FUNCTION level config");
                return funcConfig.get();
            }
        }
        
        if (module != null && !module.trim().isEmpty() && 
            project != null && !project.trim().isEmpty()) {
            Optional<EvalDimensionConfig> moduleConfig = configRepository.findModuleConfig(project, module);
            if (moduleConfig.isPresent()) {
                log.info("Found MODULE level config");
                return moduleConfig.get();
            }
        }
        
        if (project != null && !project.trim().isEmpty()) {
            Optional<EvalDimensionConfig> projectConfig = configRepository.findProjectConfig(project);
            if (projectConfig.isPresent()) {
                log.info("Found PROJECT level config");
                return projectConfig.get();
            }
        }
        
        Optional<EvalDimensionConfig> globalConfig = configRepository.findGlobalConfig();
        if (globalConfig.isPresent()) {
            log.info("Found GLOBAL level config");
            return globalConfig.get();
        }
        
        log.info("No dimension config found, using defaults");
        return null;
    }

    private EvalLlmConfig buildEvalLlmConfig(EvalDimensionConfig config, EvalModel model) {
        EvalLlmConfig llmConfig = new EvalLlmConfig();
        
        if (model != null) {
            llmConfig.setId(model.getId());
            llmConfig.setName(model.getName());
            llmConfig.setBaseUrl(model.getBaseUrl());
            llmConfig.setApiKey(model.getApiKey());
            llmConfig.setModel(model.getModelName() != null && !model.getModelName().isEmpty() ? model.getModelName() : model.getName());
            llmConfig.setTemperature(model.getTemperature());
            llmConfig.setMaxTokens(model.getMaxTokens());
            llmConfig.setTimeout(model.getTimeout());
        } else {
            llmConfig.setTemperature(0.1);
            llmConfig.setMaxTokens(256);
            llmConfig.setTimeout(30000);
        }
        
        if (config != null) {
            if (config.getSystemPrompt() != null && !config.getSystemPrompt().isEmpty()) {
                llmConfig.setSystemPrompt(config.getSystemPrompt());
            }
            llmConfig.setPassThreshold(config.getPassThreshold());
        } else {
            llmConfig.setPassThreshold(0.7);
            llmConfig.setSystemPrompt(EvalLlmConfig.buildDefaultSystemPrompt());
        }
        
        return llmConfig;
    }
}