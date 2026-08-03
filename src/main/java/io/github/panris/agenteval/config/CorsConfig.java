package io.github.panris.agenteval.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.util.Arrays;
import java.util.List;

/**
 * 生产环境 CORS 配置：限制允许的来源。
 * 通过 application-prod.yml 中的 cors.allowedOrigins 配置（逗号分隔）。
 *
 * 与 AppConfig 不同：
 * - AppConfig (@Profile("!prod"))：开发环境，Swagger 开启，CORS 完全开放
 * - CorsConfig (@Profile("prod"))：生产环境，Swagger 关闭，CORS 受限
 */
@Configuration
@Profile("prod")
public class CorsConfig implements WebMvcConfigurer {

    @Value("${cors.allowedOrigins:}")
    private String allowedOrigins;

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();

        if (allowedOrigins == null || allowedOrigins.trim().isEmpty()) {
            // 未配置：仅允许同源（浏览器同源策略）
            configuration.setAllowedOriginPatterns(List.of());
        } else if ("*".equals(allowedOrigins.trim())) {
            // 显式配置为 *：完全开放（不推荐用于生产）
            configuration.setAllowedOriginPatterns(List.of("*"));
        } else {
            // 解析逗号分隔的来源列表
            configuration.setAllowedOrigins(
                    Arrays.stream(allowedOrigins.split(","))
                            .map(String::trim)
                            .filter(s -> !s.isEmpty())
                            .toList()
            );
        }

        configuration.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "OPTIONS"));
        configuration.setAllowedHeaders(List.of("*"));
        configuration.setMaxAge(3600L);
        configuration.setAllowCredentials(true);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }
}
