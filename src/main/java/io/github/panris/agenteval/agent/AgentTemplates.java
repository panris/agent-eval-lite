package io.github.panris.agenteval.agent;

import io.github.panris.agenteval.model.AgentConfig;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Predefined agent configuration templates.
 * Provides ready-to-use configurations for popular agent APIs.
 */
public class AgentTemplates {

    /**
     * Get all available templates.
     */
    public static List<AgentConfig> getTemplates() {
        return List.of(
                createOpenAITemplate(),
                createClaudeTemplate(),
                createCustomHTTPTemplate(),
                createSimpleHTTPPTemplate()
        );
    }

    /**
     * OpenAI GPT-4 / GPT-3.5 template.
     */
    public static AgentConfig createOpenAITemplate() {
        AgentConfig config = new AgentConfig();
        config.setName("OpenAI GPT-4");
        config.setType("openai");
        config.setDescription("OpenAI Chat Completion API (GPT-4, GPT-3.5)");
        config.setEndpoint("https://api.openai.com/v1/chat/completions");
        config.setTimeout(60000);

        // Headers
        Map<String, String> headers = new HashMap<>();
        headers.put("Content-Type", "application/json");
        headers.put("Authorization", "Bearer ${apiKey}");
        config.setHeaders(headers);

        // Request mapping
        AgentConfig.RequestMapping requestMapping = new AgentConfig.RequestMapping();
        requestMapping.setTemplate("{\"model\":\"${model}\",\"messages\":[{\"role\":\"user\",\"content\":\"${input}\"}],\"temperature\":0.7}");
        requestMapping.setInputField("messages[0].content");

        Map<String, Object> staticFields = new HashMap<>();
        staticFields.put("model", "gpt-3.5-turbo");
        staticFields.put("temperature", 0.7);
        requestMapping.setStaticFields(staticFields);
        config.setRequestMapping(requestMapping);

        // Response mapping
        AgentConfig.ResponseMapping responseMapping = new AgentConfig.ResponseMapping();
        responseMapping.setOutputPath("choices[0].message.content");
        responseMapping.setErrorPath("error");
        responseMapping.setErrorMessagePath("error.message");
        config.setResponseMapping(responseMapping);

        // Type-specific config
        Map<String, Object> typeConfig = new HashMap<>();
        typeConfig.put("apiKey", "");
        typeConfig.put("model", "gpt-3.5-turbo");
        typeConfig.put("systemPrompt", "");
        config.setConfig(typeConfig);

        return config;
    }

    /**
     * Anthropic Claude template.
     */
    public static AgentConfig createClaudeTemplate() {
        AgentConfig config = new AgentConfig();
        config.setName("Anthropic Claude");
        config.setType("claude");
        config.setDescription("Anthropic Claude API (Claude 3, Claude 2)");
        config.setEndpoint("https://api.anthropic.com/v1/messages");
        config.setTimeout(60000);

        // Headers
        Map<String, String> headers = new HashMap<>();
        headers.put("Content-Type", "application/json");
        headers.put("x-api-key", "${apiKey}");
        headers.put("anthropic-version", "2023-06-01");
        config.setHeaders(headers);

        // Request mapping
        AgentConfig.RequestMapping requestMapping = new AgentConfig.RequestMapping();
        requestMapping.setTemplate("{\"model\":\"${model}\",\"max_tokens\":1024,\"messages\":[{\"role\":\"user\",\"content\":\"${input}\"}]}");
        requestMapping.setInputField("messages[0].content");

        Map<String, Object> staticFields = new HashMap<>();
        staticFields.put("model", "claude-3-sonnet-20240229");
        staticFields.put("max_tokens", 1024);
        requestMapping.setStaticFields(staticFields);
        config.setRequestMapping(requestMapping);

        // Response mapping
        AgentConfig.ResponseMapping responseMapping = new AgentConfig.ResponseMapping();
        responseMapping.setOutputPath("content[0].text");
        responseMapping.setErrorPath("error");
        responseMapping.setErrorMessagePath("error.message");
        config.setResponseMapping(responseMapping);

        // Type-specific config
        Map<String, Object> typeConfig = new HashMap<>();
        typeConfig.put("apiKey", "");
        typeConfig.put("model", "claude-3-sonnet-20240229");
        typeConfig.put("maxTokens", 1024);
        typeConfig.put("systemPrompt", "");
        config.setConfig(typeConfig);

        return config;
    }

    /**
     * Custom HTTP template with JSONPath mapping.
     */
    public static AgentConfig createCustomHTTPTemplate() {
        AgentConfig config = new AgentConfig();
        config.setName("自定义 HTTP Agent");
        config.setType("custom");
        config.setDescription("自定义 HTTP 接口，支持 JSONPath 映射");
        config.setEndpoint("http://localhost:8000/api/chat");
        config.setTimeout(30000);

        // Headers
        Map<String, String> headers = new HashMap<>();
        headers.put("Content-Type", "application/json");
        config.setHeaders(headers);

        // Request mapping
        AgentConfig.RequestMapping requestMapping = new AgentConfig.RequestMapping();
        requestMapping.setTemplate("{\"input\":\"${input}\"}");
        requestMapping.setInputField("input");
        config.setRequestMapping(requestMapping);

        // Response mapping
        AgentConfig.ResponseMapping responseMapping = new AgentConfig.ResponseMapping();
        responseMapping.setOutputPath("output");
        responseMapping.setErrorPath("error");
        responseMapping.setErrorMessagePath("error.message");
        config.setResponseMapping(responseMapping);

        // Type-specific config
        Map<String, Object> typeConfig = new HashMap<>();
        typeConfig.put("customField1", "");
        typeConfig.put("customField2", "");
        config.setConfig(typeConfig);

        return config;
    }

    /**
     * Simple HTTP template (input/output format).
     */
    public static AgentConfig createSimpleHTTPPTemplate() {
        AgentConfig config = new AgentConfig();
        config.setName("简单 HTTP Agent");
        config.setType("http");
        config.setDescription("简单 HTTP 接口：{\"input\":\"...\"} -> {\"output\":\"...\"}");
        config.setEndpoint("http://localhost:8000/api/chat");
        config.setTimeout(30000);

        // Headers
        Map<String, String> headers = new HashMap<>();
        headers.put("Content-Type", "application/json");
        config.setHeaders(headers);

        // Request mapping
        AgentConfig.RequestMapping requestMapping = new AgentConfig.RequestMapping();
        requestMapping.setTemplate("{\"input\":\"${input}\"}");
        requestMapping.setInputField("input");
        config.setRequestMapping(requestMapping);

        // Response mapping
        AgentConfig.ResponseMapping responseMapping = new AgentConfig.ResponseMapping();
        responseMapping.setOutputPath("output");
        config.setResponseMapping(responseMapping);

        return config;
    }

    /**
     * Get template by type.
     */
    public static AgentConfig getTemplateByType(String type) {
        return switch (type.toLowerCase()) {
            case "openai" -> createOpenAITemplate();
            case "claude" -> createClaudeTemplate();
            case "custom" -> createCustomHTTPTemplate();
            case "http" -> createSimpleHTTPPTemplate();
            default -> createCustomHTTPTemplate();
        };
    }
}
