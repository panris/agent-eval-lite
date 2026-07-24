package io.github.panris.agenteval.repository;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.panris.agenteval.model.EvalLlmConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Repository;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Repository
public class EvalLlmConfigRepository {
    private static final Logger logger = LoggerFactory.getLogger(EvalLlmConfigRepository.class);
    private final Map<String, EvalLlmConfig> configs = new ConcurrentHashMap<>();
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final String dataFile;

    public EvalLlmConfigRepository(@Value("${data.dir:data}") String dataDir) {
        this.dataFile = new File(dataDir, "eval-llm-configs.json").getPath();
        loadFromFile();
    }

    public List<EvalLlmConfig> findAll() { return new ArrayList<>(configs.values()); }
    public Optional<EvalLlmConfig> findById(String id) { return Optional.ofNullable(configs.get(id)); }
    public EvalLlmConfig save(EvalLlmConfig config) {
        if (config.getId() == null || config.getId().isEmpty()) config.setId(UUID.randomUUID().toString());
        config.setUpdatedAt(new Date().toInstant());
        configs.put(config.getId(), config);
        saveToFile();
        return config;
    }
    public boolean deleteById(String id) {
        if (configs.remove(id) != null) { saveToFile(); return true; }
        return false;
    }
    public boolean existsById(String id) { return configs.containsKey(id); }
    public long count() { return configs.size(); }

    private void loadFromFile() {
        try {
            File file = new File(dataFile);
            if (file.exists()) {
                Map<String, EvalLlmConfig> loaded = objectMapper.readValue(file,
                    objectMapper.getTypeFactory().constructMapType(ConcurrentHashMap.class, String.class, EvalLlmConfig.class));
                configs.clear(); configs.putAll(loaded);
                logger.info("Loaded {} eval LLM configs", configs.size());
            }
        } catch (IOException e) { logger.error("Failed to load eval LLM configs: {}", e.getMessage()); }
    }
    private void saveToFile() {
        try {
            File file = new File(dataFile); file.getParentFile().mkdirs();
            objectMapper.writerWithDefaultPrettyPrinter().writeValue(file, configs);
        } catch (IOException e) { logger.error("Failed to save eval LLM configs: {}", e.getMessage()); }
    }
}