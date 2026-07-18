package io.github.panris.agenteval.model;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * Agent configuration entity.
 * Supports multiple agent types with flexible request/response mapping.
 */
public class AgentConfig {

    private String id;
    private String name;
    private String type;  // http, openai, claude, custom
    private String description;

    // Endpoint configuration
    private String endpoint;
    private Map<String, String> headers;
    private int timeout;

    // Request mapping configuration
    private RequestMapping requestMapping;

    // Response mapping configuration
    private ResponseMapping responseMapping;

    // Type-specific configuration
    private Map<String, Object> config;  // apiKey, model, systemPrompt, etc.

    // Metadata
    private Instant createdAt;
    private Instant updatedAt;

    public AgentConfig() {
        this.id = UUID.randomUUID().toString();
        this.timeout = 30000;
        this.createdAt = Instant.now();
        this.updatedAt = Instant.now();
    }

    // Getters and Setters

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getEndpoint() {
        return endpoint;
    }

    public void setEndpoint(String endpoint) {
        this.endpoint = endpoint;
    }

    public Map<String, String> getHeaders() {
        return headers;
    }

    public void setHeaders(Map<String, String> headers) {
        this.headers = headers;
    }

    public int getTimeout() {
        return timeout;
    }

    public void setTimeout(int timeout) {
        this.timeout = timeout;
    }

    public RequestMapping getRequestMapping() {
        return requestMapping;
    }

    public void setRequestMapping(RequestMapping requestMapping) {
        this.requestMapping = requestMapping;
    }

    public ResponseMapping getResponseMapping() {
        return responseMapping;
    }

    public void setResponseMapping(ResponseMapping responseMapping) {
        this.responseMapping = responseMapping;
    }

    public Map<String, Object> getConfig() {
        return config;
    }

    public void setConfig(Map<String, Object> config) {
        this.config = config;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Instant updatedAt) {
        this.updatedAt = updatedAt;
    }

    /**
     * Request mapping configuration.
     * Defines how to construct the request body from input.
     */
    public static class RequestMapping {
        private String template;  // JSON template with placeholders
        private String inputField;  // Field path for input (e.g., "messages[0].content")
        private Map<String, Object> staticFields;  // Static fields to include

        public RequestMapping() {}

        public String getTemplate() {
            return template;
        }

        public void setTemplate(String template) {
            this.template = template;
        }

        public String getInputField() {
            return inputField;
        }

        public void setInputField(String inputField) {
            this.inputField = inputField;
        }

        public Map<String, Object> getStaticFields() {
            return staticFields;
        }

        public void setStaticFields(Map<String, Object> staticFields) {
            this.staticFields = staticFields;
        }
    }

    /**
     * Response mapping configuration.
     * Defines how to extract output from response body.
     */
    public static class ResponseMapping {
        private String outputPath;  // JSONPath to extract output (e.g., "choices[0].message.content")
        private String errorPath;  // JSONPath to check for errors
        private String errorMessagePath;  // JSONPath to extract error message

        public ResponseMapping() {}

        public String getOutputPath() {
            return outputPath;
        }

        public void setOutputPath(String outputPath) {
            this.outputPath = outputPath;
        }

        public String getErrorPath() {
            return errorPath;
        }

        public void setErrorPath(String errorPath) {
            this.errorPath = errorPath;
        }

        public String getErrorMessagePath() {
            return errorMessagePath;
        }

        public void setErrorMessagePath(String errorMessagePath) {
            this.errorMessagePath = errorMessagePath;
        }
    }
}
