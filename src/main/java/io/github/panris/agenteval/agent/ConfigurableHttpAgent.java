package io.github.panris.agenteval.agent;

import io.github.panris.agenteval.Agent;
import io.github.panris.agenteval.model.AgentConfig;
import io.github.panris.agenteval.util.JsonPathUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.*;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.util.HashMap;
import java.util.Map;

/**
 * Configurable HTTP Agent that calls external REST API.
 * Uses AgentConfig for flexible request/response mapping via JSONPath.
 */
public class ConfigurableHttpAgent implements Agent {

    private static final Logger logger = LoggerFactory.getLogger(ConfigurableHttpAgent.class);

    private final RestTemplate restTemplate;
    private final AgentConfig config;

    /**
     * Create configurable HTTP agent from AgentConfig.
     *
     * @param restTemplate the rest template (can be null, will create new if null)
     * @param config       the agent configuration
     */
    public ConfigurableHttpAgent(RestTemplate restTemplate, AgentConfig config) {
        this.restTemplate = restTemplate != null ? restTemplate : new RestTemplate();
        this.config = config;
    }

    /**
     * Create configurable HTTP agent from AgentConfig with default RestTemplate.
     *
     * @param config the agent configuration
     */
    public ConfigurableHttpAgent(AgentConfig config) {
        this(null, config);
    }

    @Override
    public String execute(String input) {
        logger.debug("Calling agent: {} at {}", config.getName(), config.getEndpoint());

        try {
            // Build request body from template
            String requestBody = buildRequestBody(input);

            // Build HTTP headers
            HttpHeaders headers = buildHeaders();

            HttpEntity<String> entity = new HttpEntity<>(requestBody, headers);

            // Make request
            ResponseEntity<String> response = restTemplate.exchange(
                config.getEndpoint(),
                HttpMethod.POST,
                entity,
                String.class
            );

            // Parse response
            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                String output = parseResponse(response.getBody());
                logger.debug("Agent response: {}", output);
                return output;
            } else {
                logger.error("Agent returned non-2xx status: {}", response.getStatusCode());
                return "ERROR: Agent returned status " + response.getStatusCode();
            }

        } catch (RestClientException e) {
            logger.error("Failed to call agent endpoint: {}", e.getMessage(), e);
            return "ERROR: " + e.getMessage();
        }
    }

    /**
     * Build request body from template and input.
     */
    private String buildRequestBody(String input) {
        AgentConfig.RequestMapping requestMapping = config.getRequestMapping();
        if (requestMapping == null) {
            // Default format
            return String.format("{\"input\":\"%s\"}", escapeJson(input));
        }

        String template = requestMapping.getTemplate();
        if (template == null || template.isEmpty()) {
            template = "{\"input\":\"${input}\"}";
        }

        // Build values map
        Map<String, Object> values = new HashMap<>();
        values.put("input", input);

        // Add static fields
        if (requestMapping.getStaticFields() != null) {
            values.putAll(requestMapping.getStaticFields());
        }

        // Add type-specific config
        if (config.getConfig() != null) {
            values.putAll(config.getConfig());
        }

        // Replace placeholders
        return JsonPathUtils.buildJson(template, values);
    }

    /**
     * Build HTTP headers from config.
     */
    private HttpHeaders buildHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        if (config.getHeaders() != null) {
            for (Map.Entry<String, String> entry : config.getHeaders().entrySet()) {
                String value = entry.getValue();

                // Replace placeholders in header values (e.g., "Bearer ${apiKey}")
                if (value != null && value.contains("${")) {
                    Map<String, Object> values = new HashMap<>();
                    if (config.getConfig() != null) {
                        values.putAll(config.getConfig());
                    }
                    value = JsonPathUtils.replacePlaceholders(value, values);
                }

                headers.set(entry.getKey(), value);
            }
        }

        return headers;
    }

    /**
     * Parse response using JSONPath mapping.
     */
    private String parseResponse(String responseBody) {
        AgentConfig.ResponseMapping responseMapping = config.getResponseMapping();
        if (responseMapping == null) {
            // Default format: {"output": "..."}
            return JsonPathUtils.extract(responseBody, "output");
        }

        // Check for error
        if (responseMapping.getErrorPath() != null) {
            String error = JsonPathUtils.extract(responseBody, responseMapping.getErrorPath());
            if (error != null && !error.isEmpty()) {
                String errorMessage = responseMapping.getErrorMessagePath() != null
                        ? JsonPathUtils.extract(responseBody, responseMapping.getErrorMessagePath())
                        : error;
                logger.error("Agent returned error: {}", errorMessage);
                return "ERROR: " + (errorMessage != null ? errorMessage : error);
            }
        }

        // Extract output
        if (responseMapping.getOutputPath() != null) {
            String output = JsonPathUtils.extract(responseBody, responseMapping.getOutputPath());
            return output != null ? output : "ERROR: No output found at path " + responseMapping.getOutputPath();
        }

        // Fallback: return entire response
        logger.warn("No output path configured, returning entire response");
        return responseBody;
    }

    /**
     * Escape JSON string.
     */
    private String escapeJson(String str) {
        if (str == null) return "";
        return str.replace("\\", "\\\\")
                  .replace("\"", "\\\"")
                  .replace("\n", "\\n")
                  .replace("\r", "\\r")
                  .replace("\t", "\\t");
    }
}
