package io.github.panris.agenteval.agent;

import io.github.panris.agenteval.Agent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.*;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.util.Map;

public abstract class BaseHttpAgent implements Agent {

    protected static final Logger logger = LoggerFactory.getLogger(BaseHttpAgent.class);

    protected final RestTemplate restTemplate;
    protected final String endpoint;
    protected final int timeoutMs;

    protected BaseHttpAgent(RestTemplate restTemplate, String endpoint, int timeoutMs) {
        this.restTemplate = restTemplate != null ? restTemplate : new RestTemplate();
        this.endpoint = endpoint;
        this.timeoutMs = timeoutMs;
    }

    protected abstract Object buildRequestBody(String input);

    protected abstract HttpHeaders buildHeaders();

    protected abstract String parseResponse(Object responseBody);

    @Override
    public String execute(String input) {
        logger.debug("Calling agent endpoint: {}", endpoint);

        try {
            Object requestBody = buildRequestBody(input);
            HttpHeaders headers = buildHeaders();
            HttpEntity<Object> entity = new HttpEntity<>(requestBody, headers);

            ResponseEntity<Object> response = restTemplate.exchange(
                    endpoint,
                    HttpMethod.POST,
                    entity,
                    Object.class
            );

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

    protected HttpHeaders createDefaultJsonHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        return headers;
    }

    protected HttpHeaders createDefaultJsonHeaders(Map<String, String> customHeaders) {
        HttpHeaders headers = createDefaultJsonHeaders();
        if (customHeaders != null) {
            customHeaders.forEach(headers::set);
        }
        return headers;
    }

    protected String parseSimpleResponse(Object response) {
        if (response instanceof Map) {
            Map<?, ?> map = (Map<?, ?>) response;
            Object output = map.get("output");
            return output != null ? output.toString() : null;
        }
        return response.toString();
    }
}