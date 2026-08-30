package io.github.panris.agenteval.agent;

import io.github.panris.agenteval.Agent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.*;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Azure OpenAI Responses API agent.
 * Uses /openai/v1/responses endpoint with api-key auth and input format.
 */
public class AzureResponsesAgent implements Agent {

    private static final Logger logger = LoggerFactory.getLogger(AzureResponsesAgent.class);

    private final RestTemplate restTemplate;
    private final String endpoint;
    private final String apiKey;
    private final String model;
    private final int timeoutMs;

    public AzureResponsesAgent(RestTemplate restTemplate, String endpoint, String apiKey, String model, int timeoutMs) {
        this.restTemplate = restTemplate != null ? restTemplate : new RestTemplate();
        this.endpoint = endpoint;
        this.apiKey = apiKey;
        this.model = model;
        this.timeoutMs = timeoutMs;
    }

    @Override
    public String execute(String input) {
        logger.debug("Calling Azure Responses API: model={}, input={}", model, input);

        try {
            Map<String, Object> requestBody = new LinkedHashMap<>();
            requestBody.put("model", model);
            requestBody.put("input", List.of(Map.of("role", "user", "content", input)));

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.set("api-key", apiKey);

            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(requestBody, headers);

            ResponseEntity<Map> response = restTemplate.exchange(
                endpoint, HttpMethod.POST, entity, Map.class
            );

            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                String output = parseResponse(response.getBody());
                logger.debug("Azure Responses output: {}", output);
                return output;
            } else {
                logger.error("Azure Responses returned non-2xx status: {}", response.getStatusCode());
                return "ERROR: Azure Responses returned status " + response.getStatusCode();
            }

        } catch (RestClientException e) {
            logger.error("Failed to call Azure Responses API: {}", e.getMessage(), e);
            return "ERROR: " + e.getMessage();
        }
    }

    @SuppressWarnings("unchecked")
    private String parseResponse(Map<String, Object> response) {
        try {
            List<Map<String, Object>> output = (List<Map<String, Object>>) response.get("output");
            if (output != null && !output.isEmpty()) {
                List<Map<String, Object>> content = (List<Map<String, Object>>) output.get(0).get("content");
                if (content != null && !content.isEmpty()) {
                    Object text = content.get(0).get("text");
                    return text != null ? text.toString() : "ERROR: Responses output text is null";
                }
            }
        } catch (Exception e) {
            logger.error("Failed to parse Azure Responses output: {}", e.getMessage(), e);
        }
        return "ERROR: Failed to parse response";
    }
}