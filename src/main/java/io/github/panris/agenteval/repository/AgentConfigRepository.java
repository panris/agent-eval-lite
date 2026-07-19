package io.github.panris.agenteval.repository;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.panris.agenteval.model.AgentConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Repository;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * Repository for Agent configurations.
 * Persists to data/agents.json file.
 */
@Repository
public class AgentConfigRepository {

    private static final Logger logger = LoggerFactory.getLogger(AgentConfigRepository.class);

    private final Map<String, AgentConfig> configs = new ConcurrentHashMap<>();
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final String dataFile;

    public AgentConfigRepository(@Value("${data.dir:data}") String dataDir) {
        this.dataFile = new File(dataDir, "agents.json").getPath();
        loadFromFile();
    }

    /**
     * Find all agent configs.
     */
    public List<AgentConfig> findAll() {
        return new ArrayList<>(configs.values());
    }

    /**
     * Find agent config by ID.
     */
    public Optional<AgentConfig> findById(String id) {
        return Optional.ofNullable(configs.get(id));
    }

    /**
     * Find agent configs by type.
     */
    public List<AgentConfig> findByType(String type) {
        return configs.values().stream()
                .filter(config -> type.equals(config.getType()))
                .collect(Collectors.toList());
    }

    /**
     * Save agent config.
     */
    public AgentConfig save(AgentConfig config) {
        if (config.getId() == null || config.getId().isEmpty()) {
            config.setId(UUID.randomUUID().toString());
        }
        config.setUpdatedAt(new Date().toInstant());
        configs.put(config.getId(), config);
        saveToFile();
        return config;
    }

    /**
     * Delete agent config by ID.
     */
    public boolean deleteById(String id) {
        AgentConfig removed = configs.remove(id);
        if (removed != null) {
            saveToFile();
            return true;
        }
        return false;
    }

    /**
     * Check if agent config exists by ID.
     */
    public boolean existsById(String id) {
        return configs.containsKey(id);
    }

    /**
     * Count all agent configs.
     */
    public long count() {
        return configs.size();
    }

    /**
     * Load from JSON file.
     */
    private void loadFromFile() {
        try {
            File file = new File(dataFile);
            if (file.exists()) {
                Map<String, AgentConfig> loaded = objectMapper.readValue(file,
                        objectMapper.getTypeFactory().constructMapType(
                                ConcurrentHashMap.class,
                                String.class,
                                AgentConfig.class
                        ));
                configs.clear();
                configs.putAll(loaded);
                logger.info("Loaded {} agent configs from {}", configs.size(), dataFile);
            } else {
                logger.info("No existing agent config file, starting with empty collection");
            }
        } catch (IOException e) {
            logger.error("Failed to load agent configs from {}: {}", dataFile, e.getMessage());
        }
    }

    /**
     * Save to JSON file.
     */
    private void saveToFile() {
        try {
            File file = new File(dataFile);
            file.getParentFile().mkdirs();
            objectMapper.writerWithDefaultPrettyPrinter().writeValue(file, configs);
            logger.debug("Saved {} agent configs to {}", configs.size(), dataFile);
        } catch (IOException e) {
            logger.error("Failed to save agent configs to {}: {}", dataFile, e.getMessage());
        }
    }
}
