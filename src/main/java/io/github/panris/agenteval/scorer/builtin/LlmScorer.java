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
            String prompt = String.format("期望输出:\n%s\n\n实际输出:\n%s\n\n请评分并返回JSON。",
                truncate(exp, 2000), truncate(act, 2000));

            String systemPrompt = config.getSystemPrompt();
            if (systemPrompt == null || systemPrompt.isBlank()) {
                systemPrompt = EvalLlmConfig.buildDefaultSystemPrompt();
            }

            Map<String, Object> body = Map.of(
                "model", config.getModel(),
                "messages", List.of(
                    Map.of("role", "system", "content", systemPrompt),
                    Map.of("role", "user", "content", prompt)
                ),
                "temperature", config.getTemperature(),
                "max_tokens", config.getMaxTokens()
            );

            String reqBody = mapper.writeValueAsString(body);

            URL url = URI.create(config.getBaseUrl()).toURL();
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("POST");
            conn.setRequestProperty("Content-Type", "application/json; charset=UTF-8");
            conn.setRequestProperty("Authorization", "Bearer " + (config.getApiKey() != null ? config.getApiKey() : ""));
            conn.setDoOutput(true);
            conn.setConnectTimeout(config.getTimeout());
            conn.setReadTimeout(config.getTimeout());

            try (OutputStream os = conn.getOutputStream()) {
                byte[] input = reqBody.getBytes(StandardCharsets.UTF_8);
                os.write(input, 0, input.length);
            }

            int status = conn.getResponseCode();
            String respBody;
            if (status >= 200 && status < 300) {
                respBody = new String(conn.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            } else {
                InputStream errStream = conn.getErrorStream();
                respBody = errStream != null ? new String(errStream.readAllBytes(), StandardCharsets.UTF_8) : "";
                return ScorerResult.failed("LLM API错误: " + status + " " + respBody);
            }

            Map<String, Object> respMap = mapper.readValue(respBody, Map.class);
            List<Map<String, Object>> choices = (List<Map<String, Object>>) respMap.get("choices");
            Map<String, Object> msg = (Map<String, Object>) choices.get(0).get("message");
            String content = (String) msg.get("content");
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
        int a = s.indexOf('{'), b = s.lastIndexOf('}');
        return a >= 0 && b > a ? s.substring(a, b + 1) : s.trim();
    }
    private String truncate(String s, int n) {
        return s == null ? "" : s.length() <= n ? s : s.substring(0, n) + "...";
    }
}