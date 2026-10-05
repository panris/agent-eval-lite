package io.github.panris.agenteval.scorer.builtin;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.panris.agenteval.AgentOutput;
import io.github.panris.agenteval.TestCase;
import io.github.panris.agenteval.model.EvalLlmConfig;
import io.github.panris.agenteval.scorer.EvaluationScorer;
import io.github.panris.agenteval.scorer.ScorerResult;

import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class LlmScorer implements EvaluationScorer {
    private static final Logger log = LoggerFactory.getLogger(LlmScorer.class);
    private static final ObjectMapper mapper = new ObjectMapper();
    private static final int LLM_MAX_ATTEMPTS = 2;
    private static final long LLM_RETRY_BACKOFF_MS = 300;
    private final EvalLlmConfig config;

    public LlmScorer(EvalLlmConfig config) {
        this.config = config;
    }

    @Override public String getName() { return "llm"; }
    @Override public String getDescription() { return "LLM评分(" + config.getModel() + ")"; }
    @Override public double getThreshold() { return config.getPassThreshold(); }

    @Override
    @SuppressWarnings("unchecked")
    public ScorerResult evaluate(TestCase tc, AgentOutput out) {
        if (out.hasError()) return ScorerResult.failed("Agent错误: " + out.getError().getMessage());
        String exp = tc.getExpectedOutput(), act = out.getOutput();
        if (exp == null || act == null) return ScorerResult.failed("缺少期望/实际输出");

        try {
            String prompt = String.format("用户输入:\n%s\n\n期望输出:\n%s\n\n实际输出:\n%s\n\n请评分并返回JSON。",
                truncate(tc.getInput(), 1000), truncate(exp, 2000), truncate(act, 2000));

            String systemPrompt = config.getSystemPrompt();
            if (systemPrompt == null || systemPrompt.isBlank()) {
                systemPrompt = EvalLlmConfig.buildDefaultSystemPrompt();
            }

            boolean isResponses = "responses".equalsIgnoreCase(config.getApiType());

            Map<String, Object> body;
            if (isResponses) {
                body = new java.util.LinkedHashMap<>();
                body.put("model", config.getModel());
                body.put("input", List.of(
                    Map.of("role", "system", "content", systemPrompt),
                    Map.of("role", "user", "content", prompt)
                ));
                body.put("temperature", config.getTemperature());
                body.put("max_output_tokens", config.getMaxTokens());
            } else {
                body = Map.of(
                    "model", config.getModel(),
                    "messages", List.of(
                        Map.of("role", "system", "content", systemPrompt),
                        Map.of("role", "user", "content", prompt)
                    ),
                    "temperature", config.getTemperature(),
                    "max_tokens", config.getMaxTokens()
                );
            }

            String reqBody = mapper.writeValueAsString(body);

            String respBody = null;
            java.io.IOException lastIoError = null;
            int attempt = 0;
            while (attempt < LLM_MAX_ATTEMPTS) {
                attempt++;
                try {
                    URL url = URI.create(config.getBaseUrl()).toURL();
                    HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                    conn.setRequestMethod("POST");
                    conn.setRequestProperty("Content-Type", "application/json; charset=UTF-8");
                    if (isResponses) {
                        conn.setRequestProperty("api-key", config.getApiKey() != null ? config.getApiKey() : "");
                    } else {
                        conn.setRequestProperty("Authorization", "Bearer " + (config.getApiKey() != null ? config.getApiKey() : ""));
                    }
                    conn.setDoOutput(true);
                    conn.setConnectTimeout(config.getTimeout());
                    conn.setReadTimeout(config.getTimeout());

                    try (OutputStream os = conn.getOutputStream()) {
                        byte[] input = reqBody.getBytes(StandardCharsets.UTF_8);
                        os.write(input, 0, input.length);
                    }

                    int status = conn.getResponseCode();
                    if (status >= 200 && status < 300) {
                        respBody = new String(conn.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
                    } else {
                        InputStream errStream = conn.getErrorStream();
                        String err = errStream != null ? new String(errStream.readAllBytes(), StandardCharsets.UTF_8) : "";
                        return ScorerResult.failed("LLM API错误: " + status + " " + err);
                    }
                    lastIoError = null;
                    break;
                } catch (java.io.IOException e) {
                    lastIoError = e;
                    if (attempt < LLM_MAX_ATTEMPTS) {
                        log.warn("LLM API 网络调用失败(第{}次)，{}ms 后重试", attempt, LLM_RETRY_BACKOFF_MS);
                        try { Thread.sleep(LLM_RETRY_BACKOFF_MS); } catch (InterruptedException ie) { Thread.currentThread().interrupt(); break; }
                    }
                }
            }
            if (lastIoError != null) {
                return ScorerResult.failed("LLM评测失败(网络异常): " + lastIoError.getMessage());
            }
            if (respBody == null) {
                return ScorerResult.failed("LLM 未返回有效响应");
            }

            Map<String, Object> respMap = mapper.readValue(respBody, Map.class);
            String content;
            if (isResponses) {
                List<Map<String, Object>> output = (List<Map<String, Object>>) respMap.get("output");
                if (output == null || output.isEmpty()) {
                    return ScorerResult.failed("LLM API错误: responses 未返回 output 字段");
                }
                List<Map<String, Object>> respContent = (List<Map<String, Object>>) output.get(0).get("content");
                content = (String) respContent.get(0).get("text");
            } else {
                List<Map<String, Object>> choices = (List<Map<String, Object>>) respMap.get("choices");
                Map<String, Object> msg = (Map<String, Object>) choices.get(0).get("message");
                content = (String) msg.get("content");
            }
            Map<String, Object> result = mapper.readValue(extractJson(content), Map.class);

            double score = ((Number) result.getOrDefault("score", 0.0)).doubleValue();
            String rationale = (String) result.getOrDefault("rationale", "");
            return ScorerResult.of(score, score >= config.getPassThreshold(), rationale,
                Map.of("model", config.getModel()));

        } catch (Exception e) {
            return ScorerResult.failed("LLM评测失败: " + e.getMessage());
        }
    }

    private String extractJson(String s) {
        if (s == null) return "{}";
        String cleaned = s.trim();
        cleaned = cleaned.replaceAll("^```json\\s*", "").replaceAll("\\s*```$", "");
        cleaned = cleaned.replaceAll("^```\\s*", "").replaceAll("\\s*```$", "");
        
        int a = cleaned.indexOf('{'), b = cleaned.lastIndexOf('}');
        if (a >= 0 && b > a) {
            String jsonPart = cleaned.substring(a, b + 1);
            return validateJson(jsonPart) ? jsonPart : "{}";
        }
        
        return validateJson(cleaned) ? cleaned : "{}";
    }
    
    private boolean validateJson(String s) {
        try {
            mapper.readValue(s, Map.class);
            return true;
        } catch (Exception e) {
            return false;
        }
    }
    private String truncate(String s, int n) {
        return s == null ? "" : s.length() <= n ? s : s.substring(0, n) + "...";
    }
}