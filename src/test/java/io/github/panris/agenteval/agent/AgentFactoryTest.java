package io.github.panris.agenteval.agent;

import io.github.panris.agenteval.Agent;
import io.github.panris.agenteval.model.AgentConfig;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestTemplate;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * AgentFactory 路由逻辑单测（纯单测，无 Spring 上下文）。
 * 覆盖各类型 → 对应 Agent 实现的创建，以及 apiKey 缺失回退 demo 的行为。
 */
class AgentFactoryTest {

    private final AgentFactory factory = new AgentFactory(new RestTemplate());

    // ============ 注入式简单 Agent（无需网络） ============

    @Test
    @DisplayName("demo agent 执行数学与回退")
    void testDemoAgent() {
        Agent a = factory.createAgent("demo");
        assertEquals("5", a.execute("2 + 3"));
        assertEquals("6", a.execute("2 * 3"));
        assertTrue(a.execute("hello").contains("[DEMO]"));
    }

    @Test
    @DisplayName("echo / upper / reverse 行为正确")
    void testSimpleAgents() {
        assertEquals("abc", factory.createAgent("echo").execute("abc"));
        assertEquals("ABC", factory.createAgent("upper").execute("abc"));
        assertEquals("cba", factory.createAgent("reverse").execute("abc"));
    }

    // ============ 配置式 Agent 路由（按 type） ============

    @Test
    @DisplayName("openai 有 apiKey → OpenAIAgent")
    void testOpenAIMapWithKey() {
        Agent a = factory.createAgent("openai", Map.of("apiKey", "sk-test", "model", "gpt-4"));
        assertInstanceOf(OpenAIAgent.class, a);
    }

    @Test
    @DisplayName("openai 无 apiKey → 回退 demo")
    void testOpenAIMapWithoutKey() {
        Agent a = factory.createAgent("openai", Map.of());
        assertTrue(a.execute("2 + 3").equals("5"));
    }

    @Test
    @DisplayName("claude 有 apiKey → ConfigurableHttpAgent")
    void testClaudeMapWithKey() {
        Agent a = factory.createAgent("claude", Map.of("apiKey", "sk-test", "model", "claude-3"));
        assertInstanceOf(ConfigurableHttpAgent.class, a);
    }

    @Test
    @DisplayName("claude 无 apiKey → 回退 demo")
    void testClaudeMapWithoutKey() {
        Agent a = factory.createAgent("claude", Map.of());
        assertTrue(a.execute("hello").contains("[DEMO]"));
    }

    @Test
    @DisplayName("azure_responses 有 apiKey → AzureResponsesAgent (新增功能)")
    void testAzureResponsesMapWithKey() {
        Agent a = factory.createAgent("azure_responses", Map.of("apiKey", "key", "model", "gpt-5.4-pro-1"));
        assertInstanceOf(AzureResponsesAgent.class, a);
    }

    @Test
    @DisplayName("azure_responses 无 apiKey → 回退 demo")
    void testAzureResponsesMapWithoutKey() {
        Agent a = factory.createAgent("azure_responses", Map.of());
        assertTrue(a.execute("hello").contains("[DEMO]"));
    }

    @Test
    @DisplayName("http 有 endpoint → HttpAgent")
    void testHttpMapWithEndpoint() {
        Agent a = factory.createAgent("http", Map.of("endpoint", "http://localhost:9999/chat"));
        assertInstanceOf(HttpAgent.class, a);
    }

    @Test
    @DisplayName("http 无 endpoint → 抛 IllegalArgumentException")
    void testHttpMapWithoutEndpoint() {
        assertThrows(IllegalArgumentException.class, () -> factory.createAgent("http", Map.of()));
    }

    @Test
    @DisplayName("未知类型 → 回退 demo")
    void testUnknownTypeFallback() {
        Agent a = factory.createAgent("nonexistent-type", Map.of());
        assertTrue(a.execute("hello").contains("[DEMO]"));
    }

    @Test
    @DisplayName("空类型 → 默认 http → 无 endpoint 抛异常")
    void testEmptyTypeFallbackToHttp() {
        assertThrows(IllegalArgumentException.class, () -> factory.createAgent("", Map.of()));
    }

    // ============ 从 AgentConfig 实体创建 ============

    @Test
    @DisplayName("createAgent(AgentConfig) openai 有 key → ConfigurableHttpAgent")
    void testFromConfigOpenAI() {
        AgentConfig c = makeConfig("openai", "sk-test");
        assertInstanceOf(ConfigurableHttpAgent.class, factory.createAgent(c));
    }

    @Test
    @DisplayName("createAgent(AgentConfig) azure_responses 有 key → AzureResponsesAgent")
    void testFromConfigAzure() {
        AgentConfig c = makeConfig("azure_responses", "key");
        assertInstanceOf(AzureResponsesAgent.class, factory.createAgent(c));
    }

    @Test
    @DisplayName("createAgent(AgentConfig) claude 有 key → ConfigurableHttpAgent")
    void testFromConfigClaude() {
        AgentConfig c = makeConfig("claude", "sk-test");
        assertInstanceOf(ConfigurableHttpAgent.class, factory.createAgent(c));
    }

    @Test
    @DisplayName("createAgent(AgentConfig) null → 回退 demo")
    void testFromConfigNull() {
        assertTrue(factory.createAgent((AgentConfig) null).execute("2 + 3").equals("5"));
    }

    // ============ Helper ============

    private AgentConfig makeConfig(String type, String apiKey) {
        AgentConfig c = new AgentConfig();
        c.setId("id");
        c.setName("test");
        c.setType(type);
        c.setEndpoint("http://localhost:8080/api");
        c.setTimeout(30000);
        Map<String, Object> cfg = new java.util.HashMap<>();
        cfg.put("apiKey", apiKey);
        cfg.put("model", "test-model");
        c.setConfig(cfg);
        return c;
    }
}
