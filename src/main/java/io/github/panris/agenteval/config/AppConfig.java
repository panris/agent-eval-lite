package io.github.panris.agenteval.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Configuration
public class AppConfig {
    private static final Logger log = LoggerFactory.getLogger(AppConfig.class);
    private static final Path SHARES_FILE = Paths.get("data/shares.json");
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Bean
    public Map<String, Map<String, Object>> reportHistory() {
        return new ConcurrentHashMap<>();
    }

    @Bean
    public Map<String, String> sharedReports() {
        return new ConcurrentHashMap<>();
    }

    /** 持久化 sharedReports 到 data/shares.json */
    public void saveSharedReports(Map<String, String> sharedReports) {
        try {
            Files.createDirectories(SHARES_FILE.getParent());
            String json = objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(sharedReports);
            Files.writeString(SHARES_FILE, json);
        } catch (Exception e) {
            log.error("Failed to save sharedReports: {}", e.getMessage(), e);
        }
    }

    /** 从 data/shares.json 加载 sharedReports */
    @SuppressWarnings("unchecked")
    public void loadSharedReports(Map<String, String> sharedReports) {
        if (!Files.exists(SHARES_FILE)) return;
        try {
            Map<String, String> loaded = objectMapper.readValue(SHARES_FILE.toFile(), Map.class);
            sharedReports.putAll(loaded);
            log.info("Loaded {} shared report links", sharedReports.size());
        } catch (Exception e) {
            log.error("Failed to load sharedReports: {}", e.getMessage(), e);
        }
    }
}
