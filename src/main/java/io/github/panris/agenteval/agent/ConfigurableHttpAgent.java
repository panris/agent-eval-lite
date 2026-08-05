package io.github.panris.agenteval.agent;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
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
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Configurable HTTP Agent that calls external REST API.
 * Uses AgentConfig for flexible request/response mapping via JSONPath.
 */
public class ConfigurableHttpAgent implements Agent {

    private static final Logger logger = LoggerFactory.getLogger(ConfigurableHttpAgent.class);
    private static final ObjectMapper objectMapper = new ObjectMapper();
    private static final Pattern PLACEHOLDER_PATTERN = Pattern.compile("\\$\\{([^}]+)}");

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

        // Build values map - input has highest priority
        Map<String, Object> values = new HashMap<>();

        // Add type-specific config first (lower priority)
        if (config.getConfig() != null) {
            values.putAll(config.getConfig());
        }

        // Add static fields
        if (requestMapping != null && requestMapping.getStaticFields() != null) {
            values.putAll(requestMapping.getStaticFields());
        }

        // Add input with highest priority (will override any same key in config/static)
        values.put("input", input);

        if (requestMapping == null) {
            // Default format
            return String.format("{\"input\":\"%s\"}", escapeJson(input));
        }

        String template = requestMapping.getTemplate();
        if (template == null || template.isEmpty()) {
            template = "{\"input\":\"${input}\"}";
        }

        // Try to parse template as JSON and process it
        try {
            JsonNode jsonNode = objectMapper.readTree(template);
            JsonNode processed = processJsonNode(jsonNode, values);
            return objectMapper.writeValueAsString(processed);
        } catch (JsonProcessingException e) {
            logger.debug("Template is not valid JSON, using string replacement: {}", e.getMessage());
            // Fallback: simple string replacement
            return JsonPathUtils.buildJson(template, values);
        }
    }

    /**
     * Recursively process JSON nodes, replacing ${...} placeholders in string values.
     */
    private JsonNode processJsonNode(JsonNode node, Map<String, Object> values) {
        if (node == null) {
            return null;
        }

        if (node.isTextual()) {
            String text = node.asText();
            if (text.contains("${")) {
                return objectMapper.getNodeFactory().textNode(replacePlaceholders(text, values));
            }
            return node;
        } else if (node.isObject()) {
            ObjectNode objectNode = ((ObjectNode) node).deepCopy();
            objectNode.fields().forEachRemaining(entry -> {
                JsonNode processed = processJsonNode(entry.getValue(), values);
                objectNode.set(entry.getKey(), processed);
            });
            return objectNode;
        } else if (node.isArray()) {
            ArrayNode arrayNode = ((ArrayNode) node).deepCopy();
            for (int i = 0; i < arrayNode.size(); i++) {
                JsonNode processed = processJsonNode(arrayNode.get(i), values);
                arrayNode.set(i, processed);
            }
            return arrayNode;
        }

        return node;
    }

    /**
     * Replace placeholders in template string with actual values.
     */
    private String replacePlaceholders(String template, Map<String, Object> values) {
        StringBuffer result = new StringBuffer();
        Matcher matcher = PLACEHOLDER_PATTERN.matcher(template);

        while (matcher.find()) {
            String key = matcher.group(1);
            Object value = values.get(key);
            String replacement;
            if (value != null) {
                if (value instanceof String) {
                    replacement = (String) value;
                } else {
                    replacement = value.toString();
                }
            } else {
                replacement = "";
            }
            matcher.appendReplacement(result, Matcher.quoteReplacement(replacement));
        }
        matcher.appendTail(result);

        return result.toString();
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
            // No mapping configured, return entire response
            logger.debug("No response mapping configured, returning entire response");
            return responseBody;
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

        // Extract output if outputPath is configured
        if (responseMapping.getOutputPath() != null && !responseMapping.getOutputPath().isEmpty()) {
            String output = JsonPathUtils.extract(responseBody, responseMapping.getOutputPath());
            return output != null ? output : "ERROR: No output found at path " + responseMapping.getOutputPath();
        }

        // Fallback: return entire response
        logger.debug("No output path configured or empty, returning entire response");
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
