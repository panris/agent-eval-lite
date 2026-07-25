package io.github.panris.agenteval.repository;

import io.github.panris.agenteval.model.EvalLlmConfig;
import io.github.panris.agenteval.model.EvalLlmConfigEntity;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Repository;

import java.util.*;
import java.util.stream.Collectors;

@Repository
public class EvalLlmConfigRepository {

    private static final Logger logger = LoggerFactory.getLogger(EvalLlmConfigRepository.class);
    private final EvalLlmConfigJpaRepository jpaRepository;

    public EvalLlmConfigRepository(EvalLlmConfigJpaRepository jpaRepository) {
        this.jpaRepository = jpaRepository;
    }

    public List<EvalLlmConfig> findAll() {
        return jpaRepository.findAll().stream()
            .map(this::entityToModel)
            .collect(Collectors.toList());
    }

    public Optional<EvalLlmConfig> findById(String id) {
        return jpaRepository.findById(id).map(this::entityToModel);
    }

    public EvalLlmConfig save(EvalLlmConfig config) {
        EvalLlmConfigEntity entity = modelToEntity(config);
        if (entity.getId() == null || entity.getId().isEmpty()) {
            entity.setId(UUID.randomUUID().toString());
        }
        entity.setUpdatedAt(new Date().toInstant());
        EvalLlmConfigEntity saved = jpaRepository.save(entity);
        logger.info("Saved eval LLM config: {}", saved.getId());
        return entityToModel(saved);
    }

    public boolean deleteById(String id) {
        if (jpaRepository.existsById(id)) {
            jpaRepository.deleteById(id);
            return true;
        }
        return false;
    }

    public boolean existsById(String id) {
        return jpaRepository.existsById(id);
    }

    public long count() {
        return jpaRepository.count();
    }

    private EvalLlmConfig entityToModel(EvalLlmConfigEntity entity) {
        EvalLlmConfig config = new EvalLlmConfig();
        config.setId(entity.getId());
        config.setName(entity.getName());
        config.setBaseUrl(entity.getBaseUrl());
        config.setApiKey(entity.getApiKey());
        config.setModel(entity.getModel());
        config.setTemperature(entity.getTemperature());
        config.setMaxTokens(entity.getMaxTokens());
        config.setTimeout(entity.getTimeout());
        config.setPassThreshold(entity.getPassThreshold());
        config.setSystemPrompt(entity.getSystemPrompt());
        config.setCreatedAt(entity.getCreatedAt());
        config.setUpdatedAt(entity.getUpdatedAt());
        return config;
    }

    private EvalLlmConfigEntity modelToEntity(EvalLlmConfig config) {
        EvalLlmConfigEntity entity = new EvalLlmConfigEntity();
        entity.setId(config.getId());
        entity.setName(config.getName());
        entity.setBaseUrl(config.getBaseUrl());
        entity.setApiKey(config.getApiKey());
        entity.setModel(config.getModel());
        entity.setTemperature(config.getTemperature());
        entity.setMaxTokens(config.getMaxTokens());
        entity.setTimeout(config.getTimeout());
        entity.setPassThreshold(config.getPassThreshold());
        entity.setSystemPrompt(config.getSystemPrompt());
        entity.setCreatedAt(config.getCreatedAt());
        entity.setUpdatedAt(config.getUpdatedAt());
        return entity;
    }
}