package io.github.panris.agenteval.agent;

import io.github.panris.agenteval.Agent;
import io.github.panris.agenteval.model.AgentConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.util.Map;

/**
 * Factory for creating Agent instances.
 * Supports HTTP, OpenAI, Claude, and custom agents based on type and configuration.
 */
@Component
public class AgentFactory {

    private static final Logger logger = LoggerFactory.getLogger(AgentFactory.class);

    private final RestTemplate restTemplate;

    public AgentFactory(RestTemplate restTemplate) {
        this.restTemplate = restTemplate;
    }

    @Value("${agent.default.type:http}")
    private String defaultAgentType;

    @Value("${agent.http.endpoint:}")
    private String httpEndpoint;

    @Value("${agent.http.timeout:30000}")
    private int httpTimeout;

    @Value("${agent.openai.api-key:}")
    private String openaiApiKey;

    @Value("${agent.openai.model:gpt-3.5-turbo}")
    private String openaiModel;

    @Value("${agent.openai.endpoint:https://api.openai.com/v1/chat/completions}")
    private String openaiEndpoint;

    @Value("${agent.openai.system-prompt:}")
    private String openaiSystemPrompt;

    /**
     * Create agent by type with configuration.
     *
     * @param type   agent type (http, openai, claude, custom, demo)
     * @param config agent configuration
     * @return the agent instance
     */
    public Agent createAgent(String type, Map<String, Object> config) {
        if (type == null || type.isEmpty()) {
            type = defaultAgentType;
        }

        logger.info("Creating agent: type={}, config={}", type, config);

        return switch (type.toLowerCase()) {
            case "http", "custom" -> createHttpAgent(config);
            case "openai" -> createOpenAIAgent(config);
            case "claude" -> createClaudeAgent(config);
            case "demo" -> createDemoAgent();
            case "echo" -> input -> input;
            case "upper" -> String::toUpperCase;
            case "reverse" -> input -> new StringBuilder(input).reverse().toString();
            default -> {
                logger.warn("Unknown agent type: {}, using demo agent", type);
                yield createDemoAgent();
            }
        };
    }

    /**
     * Create agent from AgentConfig entity.
     *
     * @param agentConfig the agent configuration entity
     * @return the agent instance
     */
    public Agent createAgent(AgentConfig agentConfig) {
        if (agentConfig == null) {
            logger.warn("AgentConfig is null, using demo agent");
            return createDemoAgent();
        }

        logger.info("Creating agent from config: {}", agentConfig.getName());

        String type = agentConfig.getType();
        if (type == null || type.isEmpty()) {
            type = "custom";
        }

        return switch (type.toLowerCase()) {
            case "openai" -> createOpenAIAgentFromConfig(agentConfig);
            case "claude" -> createClaudeAgentFromConfig(agentConfig);
            default -> new ConfigurableHttpAgent(restTemplate, agentConfig);
        };
    }

    /**
     * Create agent with default configuration.
     *
     * @param type agent type
     * @return the agent instance
     */
    public Agent createAgent(String type) {
        return createAgent(type, Map.of());
    }

    /**
     * Create HTTP agent from configuration.
     */
    private Agent createHttpAgent(Map<String, Object> config) {
        String endpoint = getStringConfig(config, "endpoint", httpEndpoint);
        int timeout = getIntConfig(config, "timeout", httpTimeout);
        Map<String, String> headers = getMapConfig(config, "headers", Map.of());

        if (endpoint == null || endpoint.isEmpty()) {
            logger.warn("HTTP agent endpoint not configured, using demo agent");
            return createDemoAgent();
        }

        // Custom request/response format if provided
        HttpAgent.RequestFormatter formatter = null;
        HttpAgent.ResponseParser parser = null;

        if (config.containsKey("requestFormat")) {
            String format = config.get("requestFormat").toString();
            formatter = createRequestFormatter(format);
        }

        if (config.containsKey("responseFormat")) {
            String format = config.get("responseFormat").toString();
            parser = createResponseParser(format);
        }

        if (formatter != null && parser != null) {
            return new HttpAgent(restTemplate, endpoint, headers, formatter, parser, timeout);
        } else {
            return new HttpAgent(restTemplate, endpoint, headers,
                    (input) -> Map.of("input", input),
                    response -> {
                        if (response instanceof Map) {
                            Map<?, ?> map = (Map<?, ?>) response;
                            Object output = map.get("output");
                            return output != null ? output.toString() : null;
                        }
                        return response.toString();
                    },
                    timeout);
        }
    }

    /**
     * Create OpenAI agent from configuration.
     */
    private Agent createOpenAIAgent(Map<String, Object> config) {
        String apiKey = getStringConfig(config, "apiKey", openaiApiKey);
        String model = getStringConfig(config, "model", openaiModel);
        String endpoint = getStringConfig(config, "endpoint", openaiEndpoint);
        String systemPrompt = getStringConfig(config, "systemPrompt", openaiSystemPrompt);
        int timeout = getIntConfig(config, "timeout", 30000);

        if (apiKey == null || apiKey.isEmpty()) {
            logger.warn("OpenAI API key not configured, using demo agent");
            return createDemoAgent();
        }

        return new OpenAIAgent(restTemplate, endpoint, apiKey, model, timeout, systemPrompt);
    }

    /**
     * Create demo agent (echo + simple math).
     */
    private Agent createDemoAgent() {
        return input -> {
            if (input.contains("+")) {
                String[] parts = input.split("\\+");
                if (parts.length == 2) {
                    try {
                        int a = Integer.parseInt(parts[0].trim().replaceAll("[^0-9]", ""));
                        int b = Integer.parseInt(parts[1].trim().replaceAll("[^0-9]", ""));
                        return String.valueOf(a + b);
                    } catch (Exception e) {
                        return "Calculation error";
                    }
                }
            }
            if (input.contains("*")) {
                String[] parts = input.split("\\*");
                if (parts.length == 2) {
                    try {
                        int a = Integer.parseInt(parts[0].trim().replaceAll("[^0-9]", ""));
                        int b = Integer.parseInt(parts[1].trim().replaceAll("[^0-9]", ""));
                        return String.valueOf(a * b);
                    } catch (Exception e) {
                        return "Calculation error";
                    }
                }
            }
            return "I'm a demo agent. You asked: " + input;
        };
    }

    /**
     * Create Claude agent from configuration.
     */
    private Agent createClaudeAgent(Map<String, Object> config) {
        String apiKey = getStringConfig(config, "apiKey", "");
        String model = getStringConfig(config, "model", "claude-3-sonnet-20240229");
        String endpoint = getStringConfig(config, "endpoint", "https://api.anthropic.com/v1/messages");
        int maxTokens = getIntConfig(config, "maxTokens", 1024);
        int timeout = getIntConfig(config, "timeout", 60000);

        if (apiKey == null || apiKey.isEmpty()) {
            logger.warn("Claude API key not configured, using demo agent");
            return createDemoAgent();
        }

        // Use ConfigurableHttpAgent with Claude template
        AgentConfig claudeConfig = AgentTemplates.createClaudeTemplate();
        claudeConfig.getConfig().put("apiKey", apiKey);
        claudeConfig.getConfig().put("model", model);
        claudeConfig.getConfig().put("maxTokens", maxTokens);
        claudeConfig.setEndpoint(endpoint);
        claudeConfig.setTimeout(timeout);

        return new ConfigurableHttpAgent(restTemplate, claudeConfig);
    }

    /**
     * Create OpenAI agent from AgentConfig entity.
     */
    private Agent createOpenAIAgentFromConfig(AgentConfig config) {
        String apiKey = config.getConfig() != null
                ? (String) config.getConfig().getOrDefault("apiKey", "")
                : "";

        if (apiKey == null || apiKey.isEmpty()) {
            logger.warn("OpenAI API key not configured, using demo agent");
            return createDemoAgent();
        }

        return new ConfigurableHttpAgent(restTemplate, config);
    }

    /**
     * Create Claude agent from AgentConfig entity.
     */
    private Agent createClaudeAgentFromConfig(AgentConfig config) {
        String apiKey = config.getConfig() != null
                ? (String) config.getConfig().getOrDefault("apiKey", "")
                : "";

        if (apiKey == null || apiKey.isEmpty()) {
            logger.warn("Claude API key not configured, using demo agent");
            return createDemoAgent();
        }

        return new ConfigurableHttpAgent(restTemplate, config);
    }

    // Helper methods for configuration extraction

    private String getStringConfig(Map<String, Object> config, String key, String defaultValue) {
        if (config.containsKey(key)) {
            Object value = config.get(key);
            return value != null ? value.toString() : defaultValue;
        }
        return defaultValue;
    }

    private int getIntConfig(Map<String, Object> config, String key, int defaultValue) {
        if (config.containsKey(key)) {
            Object value = config.get(key);
            if (value instanceof Number) {
                return ((Number) value).intValue();
            }
            try {
                return Integer.parseInt(value.toString());
            } catch (Exception e) {
                return defaultValue;
            }
        }
        return defaultValue;
    }

    @SuppressWarnings("unchecked")
    private Map<String, String> getMapConfig(Map<String, Object> config, String key, Map<String, String> defaultValue) {
        if (config.containsKey(key)) {
            Object value = config.get(key);
            if (value instanceof Map) {
                return (Map<String, String>) value;
            }
        }
        return defaultValue;
    }

    private HttpAgent.RequestFormatter createRequestFormatter(String format) {
        // Support common formats
        return switch (format.toLowerCase()) {
            case "openai" -> input -> Map.of(
                "messages", java.util.List.of(Map.of("role", "user", "content", input))
            );
            case "simple" -> input -> Map.of("input", input);
            case "prompt" -> input -> Map.of("prompt", input);
            case "query" -> input -> Map.of("query", input);
            default -> input -> Map.of("input", input);
        };
    }

    private HttpAgent.ResponseParser createResponseParser(String format) {
        // Support common formats
        return switch (format.toLowerCase()) {
            case "openai" -> response -> {
                if (response instanceof Map) {
                    Map<?, ?> map = (Map<?, ?>) response;
                    Object choices = map.get("choices");
                    if (choices instanceof java.util.List) {
                        java.util.List<?> choiceList = (java.util.List<?>) choices;
                        if (!choiceList.isEmpty() && choiceList.get(0) instanceof Map) {
                            Map<?, ?> choice = (Map<?, ?>) choiceList.get(0);
                            Object message = choice.get("message");
                            if (message instanceof Map) {
                                Object content = ((Map<?, ?>) message).get("content");
                                return content != null ? content.toString() : null;
                            }
                        }
                    }
                }
                return response.toString();
            };
            case "simple" -> response -> {
                if (response instanceof Map) {
                    Object output = ((Map<?, ?>) response).get("output");
                    return output != null ? output.toString() : response.toString();
                }
                return response.toString();
            };
            case "text" -> response -> response.toString();
            default -> response -> {
                if (response instanceof Map) {
                    Object output = ((Map<?, ?>) response).get("output");
                    return output != null ? output.toString() : response.toString();
                }
                return response.toString();
            };
        };
    }
}
