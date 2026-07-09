package io.github.panris.agenteval.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Map;
import java.util.concurrent.Executor;
import java.util.concurrent.ThreadPoolExecutor;

@Configuration
public class AppConfig {
    private static final Logger log = LoggerFactory.getLogger(AppConfig.class);
    private static final Path SHARES_FILE = Paths.get("data/shares.json");
    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * 异步评测线程池：受 Spring 生命周期管理，上下文关闭时自动优雅停机，
     * 避免固定线程池长期驻留造成线程泄漏。
     */
    @Bean(name = "evalTaskExecutor")
    public Executor evalTaskExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        int cores = Runtime.getRuntime().availableProcessors();
        executor.setCorePoolSize(cores);
        executor.setMaxPoolSize(cores * 2);
        executor.setQueueCapacity(100);
        executor.setThreadNamePrefix("eval-async-");
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(30);
        executor.initialize();
        return executor;
    }

    /** 持久化 sharedReports 到 data/shares.json */
    // Note: reportHistory and sharedReports are now owned by ReportService.
    // This class only handles shared report persistence helpers.
    // (SharedReports persistence is handled by ReportService.saveSharedReports().)
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
