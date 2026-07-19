package io.github.panris.agenteval.agent;

import io.github.panris.agenteval.Agent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.*;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.util.Map;

/**
 * HTTP-based Agent that calls external REST API.
 * Supports configurable endpoint, headers, and request/response format.
 */
public class HttpAgent implements Agent {

    private static final Logger logger = LoggerFactory.getLogger(HttpAgent.class);

    private final RestTemplate restTemplate;
    private final String endpoint;
    private final Map<String, String> headers;
    private final RequestFormatter requestFormatter;
    private final ResponseParser responseParser;
    private final int timeoutMs;

    /**
     * Create HTTP agent with custom formatters and external RestTemplate.
     *
     * @param restTemplate     the rest template (can be null, will create new if null)
     * @param endpoint         the API endpoint URL
     * @param headers          additional HTTP headers
     * @param requestFormatter custom request body formatter
     * @param responseParser   custom response parser
     * @param timeoutMs        request timeout in milliseconds
     */
    public HttpAgent(RestTemplate restTemplate, String endpoint, Map<String, String> headers,
                      RequestFormatter requestFormatter,
                      ResponseParser responseParser,
                      int timeoutMs) {
        this.restTemplate = restTemplate != null ? restTemplate : new RestTemplate();
        this.endpoint = endpoint;
        this.headers = headers != null ? headers : Map.of();
        this.requestFormatter = requestFormatter;
        this.responseParser = responseParser;
        this.timeoutMs = timeoutMs;
    }

    /**
     * Create HTTP agent with default JSON format.
     * Request: {"input": "user input"}
     * Response: {"output": "agent response"}
     *
     * @param endpoint  the API endpoint URL
     * @param headers   additional HTTP headers
     * @param timeoutMs request timeout in milliseconds
     */
    public HttpAgent(String endpoint, Map<String, String> headers, int timeoutMs) {
        this(null, endpoint, headers,
            (input) -> Map.of("input", input),
            (response) -> {
                if (response instanceof Map) {
                    Map<?, ?> map = (Map<?, ?>) response;
                    Object output = map.get("output");
                    return output != null ? output.toString() : null;
                }
                return response.toString();
            },
            timeoutMs);
    }

    /**
     * Create HTTP agent with default settings.
     * Timeout: 30 seconds
     * Format: {"input": "..."} -> {"output": "..."}
     *
     * @param endpoint the API endpoint URL
     */
    public HttpAgent(String endpoint) {
        this(endpoint, Map.of(), 30000);
    }

    @Override
    public String execute(String input) {
        logger.debug("Calling agent endpoint: {}", endpoint);

        try {
            // Build request
            Object requestBody = requestFormatter.format(input);

            // Build HTTP entity with headers
            HttpHeaders httpHeaders = new HttpHeaders();
            httpHeaders.setContentType(MediaType.APPLICATION_JSON);
            headers.forEach(httpHeaders::set);

            HttpEntity<Object> entity = new HttpEntity<>(requestBody, httpHeaders);

            // Make request
            ResponseEntity<Object> response = restTemplate.exchange(
                endpoint,
                HttpMethod.POST,
                entity,
                Object.class
            );

            // Parse response
            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                String output = responseParser.parse(response.getBody());
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
     * Functional interface for formatting request body.
     */
    @FunctionalInterface
    public interface RequestFormatter {
        Object format(String input);
    }

    /**
     * Functional interface for parsing response body.
     */
    @FunctionalInterface
    public interface ResponseParser {
        String parse(Object response);
    }
}
