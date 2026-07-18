package io.github.panris.agenteval.util;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.jayway.jsonpath.JsonPath;
import com.jayway.jsonpath.PathNotFoundException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * JSONPath utility for flexible JSON mapping.
 * Supports both Jayway JSONPath and simple path syntax.
 */
public class JsonPathUtils {

    private static final Logger logger = LoggerFactory.getLogger(JsonPathUtils.class);
    private static final ObjectMapper objectMapper = new ObjectMapper();
    private static final Pattern PLACEHOLDER_PATTERN = Pattern.compile("\\$\\{([^}]+)}");

    /**
     * Extract value from JSON using path.
     * Supports both JSONPath (e.g., "$.choices[0].message.content") and simple path (e.g., "choices[0].message.content").
     *
     * @param json JSON string or object
     * @param path Path expression
     * @return Extracted value as string, or null if not found
     */
    public static String extract(Object json, String path) {
        if (json == null || path == null || path.isEmpty()) {
            return null;
        }

        try {
            // Convert to JSON string if needed
            String jsonStr = json instanceof String ? (String) json : objectMapper.writeValueAsString(json);

            // Try JSONPath first (starting with $)
            if (path.startsWith("$")) {
                try {
                    Object result = JsonPath.read(jsonStr, path);
                    return result != null ? result.toString() : null;
                } catch (PathNotFoundException e) {
                    logger.debug("JSONPath not found: {}", path);
                    return null;
                }
            }

            // Try simple path notation (e.g., "choices[0].message.content")
            return extractBySimplePath(objectMapper.readTree(jsonStr), path);

        } catch (Exception e) {
            logger.error("Failed to extract value from JSON at path {}: {}", path, e.getMessage());
            return null;
        }
    }

    /**
     * Extract value using simple path notation.
     * Examples: "output", "choices[0].message.content", "data.items[0].text"
     */
    private static String extractBySimplePath(JsonNode node, String path) {
        String[] parts = path.split("\\.");
        JsonNode current = node;

        for (String part : parts) {
            if (current == null || current.isNull()) {
                return null;
            }

            // Handle array index (e.g., "choices[0]")
            if (part.contains("[")) {
                String fieldName = part.substring(0, part.indexOf("["));
                int index = Integer.parseInt(part.substring(part.indexOf("[") + 1, part.indexOf("]")));

                if (!fieldName.isEmpty()) {
                    current = current.get(fieldName);
                }
                if (current != null && current.isArray()) {
                    current = current.get(index);
                }
            } else {
                current = current.get(part);
            }
        }

        if (current != null && !current.isNull()) {
            if (current.isTextual()) {
                return current.asText();
            } else if (current.isValueNode()) {
                return current.asText();
            } else {
                return current.toString();
            }
        }

        return null;
    }

    /**
     * Replace placeholders in template with actual values.
     * Example: "Hello ${name}" with {"name": "World"} -> "Hello World"
     *
     * @param template Template string with ${...} placeholders
     * @param values   Map of placeholder names to values
     * @return Processed string with placeholders replaced
     */
    public static String replacePlaceholders(String template, Map<String, Object> values) {
        if (template == null || values == null) {
            return template;
        }

        StringBuffer result = new StringBuffer();
        Matcher matcher = PLACEHOLDER_PATTERN.matcher(template);

        while (matcher.find()) {
            String key = matcher.group(1);
            Object value = values.get(key);
            String replacement = value != null ? value.toString() : "";
            matcher.appendReplacement(result, Matcher.quoteReplacement(replacement));
        }
        matcher.appendTail(result);

        return result.toString();
    }

    /**
     * Build JSON from template and values.
     * Example: template = {"input": "${input}", "model": "gpt-4"}
     *          values = {"input": "Hello"}
     *          result = {"input": "Hello", "model": "gpt-4"}
     *
     * @param template JSON template string
     * @param values   Map of placeholder names to values
     * @return JSON string with placeholders replaced
     */
    public static String buildJson(String template, Map<String, Object> values) {
        return replacePlaceholders(template, values);
    }

    /**
     * Set value at path in JSON node.
     * Creates intermediate nodes if they don't exist.
     *
     * @param root  Root JSON node
     * @param path  Path (e.g., "choices[0].message.content")
     * @param value Value to set
     */
    public static void setAtPath(ObjectNode root, String path, String value) {
        if (root == null || path == null || path.isEmpty()) {
            return;
        }

        String[] parts = path.split("\\.");
        JsonNode current = root;

        for (int i = 0; i < parts.length - 1; i++) {
            String part = parts[i];

            // Handle array index
            if (part.contains("[")) {
                String fieldName = part.substring(0, part.indexOf("["));
                int index = Integer.parseInt(part.substring(part.indexOf("[") + 1, part.indexOf("]")));

                if (!fieldName.isEmpty()) {
                    if (!current.has(fieldName)) {
                        ((ObjectNode) current).putObject(fieldName);
                    }
                    current = current.get(fieldName);
                }

                if (current.isArray()) {
                    ArrayNode array = (ArrayNode) current;
                    while (array.size() <= index) {
                        array.addObject();
                    }
                    current = array.get(index);
                }
            } else {
                if (!current.has(part)) {
                    ((ObjectNode) current).putObject(part);
                }
                current = current.get(part);
            }
        }

        // Set the final value
        String lastPart = parts[parts.length - 1];
        if (lastPart.contains("[")) {
            // Handle array at final level
            String fieldName = lastPart.substring(0, lastPart.indexOf("["));
            int index = Integer.parseInt(lastPart.substring(lastPart.indexOf("[") + 1, lastPart.indexOf("]")));

            if (!fieldName.isEmpty() && current.has(fieldName)) {
                current = current.get(fieldName);
            }
            if (current.isArray()) {
                ArrayNode array = (ArrayNode) current;
                while (array.size() <= index) {
                    array.addObject();
                }
                ((ObjectNode) array.get(index)).put("content", value);
            }
        } else {
            if (current instanceof ObjectNode) {
                ((ObjectNode) current).put(lastPart, value);
            }
        }
    }
}
