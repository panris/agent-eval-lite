package io.github.panris.agenteval.agent;

import io.github.panris.agenteval.Agent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.*;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.util.List;
import java.util.Map;

/**
 * OpenAI-compatible Agent that calls Chat Completion API.
 * Supports GPT-4, GPT-3.5, and any OpenAI-compatible endpoint.
 */
public class OpenAIAgent implements Agent {

    private static final Logger logger = LoggerFactory.getLogger(OpenAIAgent.class);

    private final RestTemplate restTemplate;
    private final String endpoint;
    private final String apiKey;
    private final String model;
    private final int timeoutMs;
    private final String systemPrompt;

    /**
     * Create OpenAI agent with external RestTemplate.
     *
     * @param restTemplate the rest template (can be null, will create new if null)
     * @param endpoint     the API endpoint (e.g., "https://api.openai.com/v1/chat/completions")
     * @param apiKey       the API key
     * @param model        the model name (e.g., "gpt-4", "gpt-3.5-turbo")
     * @param timeoutMs    request timeout in milliseconds
     * @param systemPrompt optional system prompt (can be null)
     */
    public OpenAIAgent(RestTemplate restTemplate, String endpoint, String apiKey, String model, int timeoutMs, String systemPrompt) {
        this.restTemplate = restTemplate != null ? restTemplate : new RestTemplate();
        this.endpoint = endpoint;
        this.apiKey = apiKey;
        this.model = model;
        this.timeoutMs = timeoutMs;
        this.systemPrompt = systemPrompt;
    }

    /**
     * Create OpenAI agent with default endpoint and timeout.
     *
     * @param apiKey the API key
     * @param model  the model name
     */
    public OpenAIAgent(String apiKey, String model) {
        this(null, "https://api.openai.com/v1/chat/completions", apiKey, model, 30000, null);
    }

    /**
     * Create OpenAI agent with system prompt.
     *
     * @param apiKey       the API key
     * @param model        the model name
     * @param systemPrompt the system prompt
     */
    public OpenAIAgent(String apiKey, String model, String systemPrompt) {
        this(null, "https://api.openai.com/v1/chat/completions", apiKey, model, 30000, systemPrompt);
    }

    @Override
    public String execute(String input) {
        logger.debug("Calling OpenAI API: model={}, input={}", model, input);

        try {
            // Build request body
            Map<String, Object> requestBody = buildRequestBody(input);

            // Build HTTP headers
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.setBearerAuth(apiKey);

            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(requestBody, headers);

            // Make request
            ResponseEntity<Map> response = restTemplate.exchange(
                endpoint,
                HttpMethod.POST,
                entity,
                Map.class
            );

            // Parse response
            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                String output = parseResponse(response.getBody());
                logger.debug("OpenAI response: {}", output);
                return output;
            } else {
                logger.error("OpenAI returned non-2xx status: {}", response.getStatusCode());
                return "ERROR: OpenAI returned status " + response.getStatusCode();
            }

        } catch (RestClientException e) {
            logger.error("Failed to call OpenAI API: {}", e.getMessage(), e);
            return "ERROR: " + e.getMessage();
        }
    }

    private Map<String, Object> buildRequestBody(String input) {
        // Build messages array
        List<Map<String, String>> messages;
        if (systemPrompt != null && !systemPrompt.isEmpty()) {
            messages = List.of(
                Map.of("role", "system", "content", systemPrompt),
                Map.of("role", "user", "content", input)
            );
        } else {
            messages = List.of(
                Map.of("role", "user", "content", input)
            );
        }

        return Map.of(
            "model", model,
            "messages", messages,
            "temperature", 0.7
        );
    }

    @SuppressWarnings("unchecked")
    private String parseResponse(Map<String, Object> response) {
        try {
            List<Map<String, Object>> choices = (List<Map<String, Object>>) response.get("choices");
            if (choices != null && !choices.isEmpty()) {
                Map<String, Object> choice = choices.get(0);
                Map<String, Object> message = (Map<String, Object>) choice.get("message");
                if (message != null) {
                    Object content = message.get("content");
                    return content != null ? content.toString() : null;
                }
            }
        } catch (Exception e) {
            logger.error("Failed to parse OpenAI response: {}", e.getMessage(), e);
        }
        return "ERROR: Failed to parse response";
    }
}
