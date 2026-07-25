package io.github.panris.agenteval.repository;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.panris.agenteval.model.AgentConfig;
import io.github.panris.agenteval.model.AgentConfigEntity;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Repository;

import java.util.*;
import java.util.stream.Collectors;

@Repository
public class AgentConfigRepository {

    private static final Logger logger = LoggerFactory.getLogger(AgentConfigRepository.class);
    private final AgentConfigJpaRepository jpaRepository;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public AgentConfigRepository(AgentConfigJpaRepository jpaRepository) {
        this.jpaRepository = jpaRepository;
    }

    public List<AgentConfig> findAll() {
        return jpaRepository.findAll().stream()
            .map(this::entityToModel)
            .collect(Collectors.toList());
    }

    public Optional<AgentConfig> findById(String id) {
        return jpaRepository.findById(id).map(this::entityToModel);
    }

    public List<AgentConfig> findByType(String type) {
        return jpaRepository.findByType(type).stream()
            .map(this::entityToModel)
            .collect(Collectors.toList());
    }

    public AgentConfig save(AgentConfig config) {
        AgentConfigEntity entity = modelToEntity(config);
        if (entity.getId() == null || entity.getId().isEmpty()) {
            entity.setId(UUID.randomUUID().toString());
        }
        entity.setUpdatedAt(new Date().toInstant());
        AgentConfigEntity saved = jpaRepository.save(entity);
        logger.info("Saved agent config: {}", saved.getId());
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

    private AgentConfig entityToModel(AgentConfigEntity entity) {
        AgentConfig config = new AgentConfig();
        config.setId(entity.getId());
        config.setName(entity.getName());
        config.setType(entity.getType());
        config.setDescription(entity.getDescription());
        config.setEndpoint(entity.getEndpoint());
        config.setTimeout(entity.getTimeout());
        config.setCreatedAt(entity.getCreatedAt());
        config.setUpdatedAt(entity.getUpdatedAt());

        if (entity.getHeadersJson() != null) {
            try {
                config.setHeaders(objectMapper.readValue(entity.getHeadersJson(),
                    new TypeReference<Map<String, String>>() {}));
            } catch (Exception e) {
                logger.warn("Failed to parse headers: {}", e.getMessage());
            }
        }

        if (entity.getRequestMappingJson() != null) {
            try {
                config.setRequestMapping(objectMapper.readValue(entity.getRequestMappingJson(),
                    AgentConfig.RequestMapping.class));
            } catch (Exception e) {
                logger.warn("Failed to parse request mapping: {}", e.getMessage());
            }
        }

        if (entity.getResponseMappingJson() != null) {
            try {
                config.setResponseMapping(objectMapper.readValue(entity.getResponseMappingJson(),
                    AgentConfig.ResponseMapping.class));
            } catch (Exception e) {
                logger.warn("Failed to parse response mapping: {}", e.getMessage());
            }
        }

        if (entity.getConfigJson() != null) {
            try {
                config.setConfig(objectMapper.readValue(entity.getConfigJson(),
                    new TypeReference<Map<String, Object>>() {}));
            } catch (Exception e) {
                logger.warn("Failed to parse config: {}", e.getMessage());
            }
        }

        return config;
    }

    private AgentConfigEntity modelToEntity(AgentConfig config) {
        AgentConfigEntity entity = new AgentConfigEntity();
        entity.setId(config.getId());
        entity.setName(config.getName());
        entity.setType(config.getType());
        entity.setDescription(config.getDescription());
        entity.setEndpoint(config.getEndpoint());
        entity.setTimeout(config.getTimeout());
        entity.setCreatedAt(config.getCreatedAt());
        entity.setUpdatedAt(config.getUpdatedAt());

        if (config.getHeaders() != null) {
            try {
                entity.setHeadersJson(objectMapper.writeValueAsString(config.getHeaders()));
            } catch (Exception e) {
                logger.warn("Failed to serialize headers: {}", e.getMessage());
            }
        }

        if (config.getRequestMapping() != null) {
            try {
                entity.setRequestMappingJson(objectMapper.writeValueAsString(config.getRequestMapping()));
            } catch (Exception e) {
                logger.warn("Failed to serialize request mapping: {}", e.getMessage());
            }
        }

        if (config.getResponseMapping() != null) {
            try {
                entity.setResponseMappingJson(objectMapper.writeValueAsString(config.getResponseMapping()));
            } catch (Exception e) {
                logger.warn("Failed to serialize response mapping: {}", e.getMessage());
            }
        }

        if (config.getConfig() != null) {
            try {
                entity.setConfigJson(objectMapper.writeValueAsString(config.getConfig()));
            } catch (Exception e) {
                logger.warn("Failed to serialize config: {}", e.getMessage());
            }
        }

        return entity;
    }
}