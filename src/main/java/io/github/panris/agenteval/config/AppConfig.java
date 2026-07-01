package io.github.panris.agenteval.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Configuration
public class AppConfig {

    @Bean
    public Map<String, Map<String, Object>> reportHistory() {
        return new ConcurrentHashMap<>();
    }

    @Bean
    public Map<String, String> sharedReports() {
        return new ConcurrentHashMap<>();
    }
}
