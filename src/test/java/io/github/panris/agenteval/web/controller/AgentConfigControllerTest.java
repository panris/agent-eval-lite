package io.github.panris.agenteval.web.controller;

import io.github.panris.agenteval.Agent;
import io.github.panris.agenteval.agent.AgentFactory;
import io.github.panris.agenteval.model.AgentConfig;
import io.github.panris.agenteval.repository.AgentConfigRepository;
import org.junit.jupiter.api.*;
import org.springframework.http.ResponseEntity;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Pure unit tests for AgentConfigController — no Spring context needed.
 * Controller is constructed directly with mocked dependencies.
 */
class AgentConfigControllerTest {

    private AgentConfigController controller;
    private AgentConfigRepository mockRepo;
    private AgentFactory mockFactory;

    @BeforeEach
    void setUp() {
        mockRepo = mock(AgentConfigRepository.class);
        mockFactory = mock(AgentFactory.class);
        controller = new AgentConfigController(mockRepo, mockFactory);
    }

    // ============ Helper ============

    private AgentConfig makeConfig(String id, String name, String type, String endpoint) {
        AgentConfig c = new AgentConfig();
        c.setId(id);
        c.setName(name);
        c.setType(type);
        c.setEndpoint(endpoint);
        c.setTimeout(30000);
        return c;
    }

    // ============================================================
    // GET /api/agents — List all
    // ============================================================

    @Test
    @DisplayName("GET /api/agents returns all agents with success=true")
    void testGetAllAgents() {
        AgentConfig a1 = makeConfig("id1", "GPT-4", "openai", "https://api.openai.com");
        AgentConfig a2 = makeConfig("id2", "Claude", "claude", "https://api.anthropic.com");
        when(mockRepo.findAll()).thenReturn(List.of(a1, a2));

        ResponseEntity<Map<String, Object>> resp = controller.getAllAgents();

        assertEquals(200, resp.getStatusCode().value());
        Map<String, Object> body = resp.getBody();
        assertNotNull(body);
        assertEquals(true, body.get("success"));
        @SuppressWarnings("unchecked")
        List<AgentConfig> agents = (List<AgentConfig>) body.get("agents");
        assertEquals(2, agents.size());
        assertEquals(2, body.get("total"));
    }

    @Test
    @DisplayName("GET /api/agents returns empty list when no agents")
    void testGetAllAgentsEmpty() {
        when(mockRepo.findAll()).thenReturn(List.of());

        ResponseEntity<Map<String, Object>> resp = controller.getAllAgents();

        assertEquals(200, resp.getStatusCode().value());
        Map<String, Object> body = resp.getBody();
        assertNotNull(body);
        assertEquals(true, body.get("success"));
        @SuppressWarnings("unchecked")
        List<AgentConfig> agents = (List<AgentConfig>) body.get("agents");
        assertTrue(agents.isEmpty());
        assertEquals(0, body.get("total"));
    }

    // ============================================================
    // GET /api/agents/{id} — Get by ID
    // ============================================================

    @Test
    @DisplayName("GET /api/agents/{id} returns agent when found")
    void testGetAgentById() {
        AgentConfig agent = makeConfig("id1", "GPT-4", "openai", "https://api.openai.com");
        when(mockRepo.findById("id1")).thenReturn(Optional.of(agent));

        ResponseEntity<Map<String, Object>> resp = controller.getAgentById("id1");

        assertEquals(200, resp.getStatusCode().value());
        Map<String, Object> body = resp.getBody();
        assertEquals(true, body.get("success"));
        @SuppressWarnings("unchecked")
        AgentConfig returned = (AgentConfig) body.get("agent");
        assertEquals("id1", returned.getId());
        assertEquals("GPT-4", returned.getName());
    }

    @Test
    @DisplayName("GET /api/agents/{id} returns 404 when not found")
    void testGetAgentByIdNotFound() {
        when(mockRepo.findById("not-exist")).thenReturn(Optional.empty());

        ResponseEntity<Map<String, Object>> resp = controller.getAgentById("not-exist");

        assertEquals(404, resp.getStatusCode().value());
        Map<String, Object> body = resp.getBody();
        assertNotNull(body);
        assertEquals(false, body.get("success"));
        assertNotNull(body.get("error"));
    }

    // ============================================================
    // POST /api/agents — Create
    // ============================================================

    @Test
    @DisplayName("POST /api/agents creates agent successfully")
    void testCreateAgent() {
        AgentConfig input = makeConfig(null, "New Agent", "http", "https://example.com/api");
        when(mockRepo.save(any(AgentConfig.class))).thenAnswer(inv -> {
            AgentConfig c = inv.getArgument(0);
            c.setId("generated-id");
            return c;
        });

        ResponseEntity<Map<String, Object>> resp = controller.createAgent(input);

        assertEquals(200, resp.getStatusCode().value());
        Map<String, Object> body = resp.getBody();
        assertEquals(true, body.get("success"));
        assertEquals("Agent配置创建成功", body.get("message"));
        verify(mockRepo).save(input);
    }

    @Test
    @DisplayName("POST /api/agents rejects empty name")
    void testCreateAgentEmptyName() {
        AgentConfig input = makeConfig(null, "", "http", "https://example.com");

        ResponseEntity<Map<String, Object>> resp = controller.createAgent(input);

        assertEquals(400, resp.getStatusCode().value());
        Map<String, Object> body = resp.getBody();
        assertEquals(false, body.get("success"));
        assertTrue(body.get("error").toString().contains("名称"));
        verify(mockRepo, never()).save(any());
    }

    @Test
    @DisplayName("POST /api/agents rejects null name")
    void testCreateAgentNullName() {
        AgentConfig input = makeConfig(null, null, "http", "https://example.com");

        ResponseEntity<Map<String, Object>> resp = controller.createAgent(input);

        assertEquals(400, resp.getStatusCode().value());
        Map<String, Object> body = resp.getBody();
        assertEquals(false, body.get("success"));
        verify(mockRepo, never()).save(any());
    }

    @Test
    @DisplayName("POST /api/agents rejects empty type")
    void testCreateAgentEmptyType() {
        AgentConfig input = makeConfig(null, "Agent", "", "https://example.com");

        ResponseEntity<Map<String, Object>> resp = controller.createAgent(input);

        assertEquals(400, resp.getStatusCode().value());
        Map<String, Object> body = resp.getBody();
        assertEquals(false, body.get("success"));
        assertTrue(body.get("error").toString().contains("类型"));
        verify(mockRepo, never()).save(any());
    }

    @Test
    @DisplayName("POST /api/agents rejects empty endpoint")
    void testCreateAgentEmptyEndpoint() {
        AgentConfig input = makeConfig(null, "Agent", "openai", "");

        ResponseEntity<Map<String, Object>> resp = controller.createAgent(input);

        assertEquals(400, resp.getStatusCode().value());
        Map<String, Object> body = resp.getBody();
        assertEquals(false, body.get("success"));
        assertTrue(body.get("error").toString().contains("端点"));
        verify(mockRepo, never()).save(any());
    }

    @Test
    @DisplayName("POST /api/agents sets default timeout when <= 0")
    void testCreateAgentDefaultTimeout() {
        AgentConfig input = makeConfig(null, "Agent", "http", "https://example.com");
        input.setTimeout(0);
        when(mockRepo.save(any(AgentConfig.class))).thenAnswer(inv -> {
            AgentConfig c = inv.getArgument(0);
            c.setId("id");
            return c;
        });

        controller.createAgent(input);

        assertEquals(30000, input.getTimeout());
    }

    @Test
    @DisplayName("POST /api/agents preserves positive timeout")
    void testCreateAgentPreservesTimeout() {
        AgentConfig input = makeConfig(null, "Agent", "http", "https://example.com");
        input.setTimeout(60000);
        when(mockRepo.save(any(AgentConfig.class))).thenAnswer(inv -> {
            AgentConfig c = inv.getArgument(0);
            c.setId("id");
            return c;
        });

        controller.createAgent(input);

        assertEquals(60000, input.getTimeout());
    }

    @Test
    @DisplayName("POST /api/agents preserves ID set by constructor")
    void testCreateAgentPreservesId() {
        // AgentConfig constructor generates a UUID by default
        AgentConfig input = new AgentConfig();
        input.setName("Agent");
        input.setType("http");
        input.setEndpoint("https://example.com");
        String originalId = input.getId();
        assertNotNull(originalId);
        when(mockRepo.save(any(AgentConfig.class))).thenAnswer(inv -> inv.getArgument(0));

        controller.createAgent(input);

        verify(mockRepo).save(input);
        assertEquals(originalId, input.getId());
    }

    // ============================================================
    // PUT /api/agents/{id} — Update
    // ============================================================

    @Test
    @DisplayName("PUT /api/agents/{id} updates agent successfully")
    void testUpdateAgent() {
        AgentConfig existing = makeConfig("id1", "Old Name", "http", "https://old.com");
        AgentConfig update = makeConfig("id1", "New Name", "http", "https://new.com");
        when(mockRepo.existsById("id1")).thenReturn(true);
        when(mockRepo.save(any(AgentConfig.class))).thenAnswer(inv -> inv.getArgument(0));

        ResponseEntity<Map<String, Object>> resp = controller.updateAgent("id1", update);

        assertEquals(200, resp.getStatusCode().value());
        Map<String, Object> body = resp.getBody();
        assertEquals(true, body.get("success"));
        assertEquals("Agent配置更新成功", body.get("message"));
        assertEquals("id1", update.getId());
    }

    @Test
    @DisplayName("PUT /api/agents/{id} returns 404 when not found")
    void testUpdateAgentNotFound() {
        AgentConfig update = makeConfig("not-exist", "Name", "http", "https://example.com");
        when(mockRepo.existsById("not-exist")).thenReturn(false);

        ResponseEntity<Map<String, Object>> resp = controller.updateAgent("not-exist", update);

        assertEquals(404, resp.getStatusCode().value());
        Map<String, Object> body = resp.getBody();
        assertEquals(false, body.get("success"));
        verify(mockRepo, never()).save(any());
    }

    @Test
    @DisplayName("PUT /api/agents/{id} replaces ID in path")
    void testUpdateAgentIdFromPath() {
        AgentConfig existing = makeConfig("id1", "Old", "http", "https://old.com");
        AgentConfig update = makeConfig("wrong-id", "New", "http", "https://new.com");
        when(mockRepo.existsById("id1")).thenReturn(true);
        when(mockRepo.save(any(AgentConfig.class))).thenAnswer(inv -> inv.getArgument(0));

        controller.updateAgent("id1", update);

        assertEquals("id1", update.getId());
    }

    // ============================================================
    // DELETE /api/agents/{id} — Delete
    // ============================================================

    @Test
    @DisplayName("DELETE /api/agents/{id} deletes agent successfully")
    void testDeleteAgent() {
        when(mockRepo.deleteById("id1")).thenReturn(true);

        ResponseEntity<Map<String, Object>> resp = controller.deleteAgent("id1");

        assertEquals(200, resp.getStatusCode().value());
        Map<String, Object> body = resp.getBody();
        assertEquals(true, body.get("success"));
        assertEquals("Agent配置删除成功", body.get("message"));
    }

    @Test
    @DisplayName("DELETE /api/agents/{id} returns 404 when not found")
    void testDeleteAgentNotFound() {
        when(mockRepo.deleteById("not-exist")).thenReturn(false);

        ResponseEntity<Map<String, Object>> resp = controller.deleteAgent("not-exist");

        assertEquals(404, resp.getStatusCode().value());
        Map<String, Object> body = resp.getBody();
        assertEquals(false, body.get("success"));
        assertEquals("Agent配置不存在", body.get("error"));
    }

    @Test
    @DisplayName("DELETE /api/agents/{id} returns false when ID null")
    void testDeleteAgentNullId() {
        // Repository should return false for non-existent
        when(mockRepo.deleteById(null)).thenReturn(false);

        ResponseEntity<Map<String, Object>> resp = controller.deleteAgent(null);

        assertEquals(404, resp.getStatusCode().value());
    }

    // ============================================================
    // GET /api/agents/templates — Templates
    // ============================================================

    @Test
    @DisplayName("GET /api/agents/templates returns all 4 templates")
    void testGetTemplates() {
        ResponseEntity<Map<String, Object>> resp = controller.getTemplates();

        assertEquals(200, resp.getStatusCode().value());
        Map<String, Object> body = resp.getBody();
        assertEquals(true, body.get("success"));
        @SuppressWarnings("unchecked")
        List<AgentConfig> templates = (List<AgentConfig>) body.get("templates");
        assertEquals(4, templates.size());
        // Verify types
        Set<String> types = new HashSet<>();
        templates.forEach(t -> types.add(t.getType()));
        assertTrue(types.contains("openai"));
        assertTrue(types.contains("claude"));
        assertTrue(types.contains("custom"));
        assertTrue(types.contains("http"));
    }

    @Test
    @DisplayName("GET /api/agents/templates/openai returns OpenAI template")
    void testGetTemplateByTypeOpenAI() {
        ResponseEntity<Map<String, Object>> resp = controller.getTemplateByType("openai");

        assertEquals(200, resp.getStatusCode().value());
        Map<String, Object> body = resp.getBody();
        assertEquals(true, body.get("success"));
        @SuppressWarnings("unchecked")
        AgentConfig template = (AgentConfig) body.get("template");
        assertEquals("openai", template.getType());
        assertNotNull(template.getEndpoint());
    }

    @Test
    @DisplayName("GET /api/agents/templates/claude returns Claude template")
    void testGetTemplateByTypeClaude() {
        ResponseEntity<Map<String, Object>> resp = controller.getTemplateByType("claude");

        assertEquals(200, resp.getStatusCode().value());
        Map<String, Object> body = resp.getBody();
        assertEquals(true, body.get("success"));
        @SuppressWarnings("unchecked")
        AgentConfig template = (AgentConfig) body.get("template");
        assertEquals("claude", template.getType());
    }

    @Test
    @DisplayName("GET /api/agents/templates/http returns HTTP template")
    void testGetTemplateByTypeHttp() {
        ResponseEntity<Map<String, Object>> resp = controller.getTemplateByType("http");

        assertEquals(200, resp.getStatusCode().value());
        Map<String, Object> body = resp.getBody();
        assertEquals(true, body.get("success"));
        @SuppressWarnings("unchecked")
        AgentConfig template = (AgentConfig) body.get("template");
        assertEquals("http", template.getType());
    }

    @Test
    @DisplayName("GET /api/agents/templates/custom returns custom template")
    void testGetTemplateByTypeCustom() {
        ResponseEntity<Map<String, Object>> resp = controller.getTemplateByType("custom");

        assertEquals(200, resp.getStatusCode().value());
        Map<String, Object> body = resp.getBody();
        assertEquals(true, body.get("success"));
        @SuppressWarnings("unchecked")
        AgentConfig template = (AgentConfig) body.get("template");
        assertEquals("custom", template.getType());
    }

    @Test
    @DisplayName("GET /api/agents/templates/unknown returns custom template as fallback")
    void testGetTemplateByTypeUnknown() {
        ResponseEntity<Map<String, Object>> resp = controller.getTemplateByType("unknown-type");

        assertEquals(200, resp.getStatusCode().value());
        Map<String, Object> body = resp.getBody();
        assertEquals(true, body.get("success"));
        @SuppressWarnings("unchecked")
        AgentConfig template = (AgentConfig) body.get("template");
        // Falls back to custom template
        assertEquals("custom", template.getType());
    }

    // ============================================================
    // POST /api/agents/from-template/{type} — Create from template
    // ============================================================

    @Test
    @DisplayName("POST /api/agents/from-template/openai creates from template without overrides")
    void testCreateFromTemplateNoOverrides() {
        when(mockRepo.save(any(AgentConfig.class))).thenAnswer(inv -> {
            AgentConfig c = inv.getArgument(0);
            c.setId("new-id");
            return c;
        });

        ResponseEntity<Map<String, Object>> resp = controller.createFromTemplate("openai", null);

        assertEquals(200, resp.getStatusCode().value());
        Map<String, Object> body = resp.getBody();
        assertEquals(true, body.get("success"));
        assertEquals("Agent配置创建成功", body.get("message"));
        @SuppressWarnings("unchecked")
        AgentConfig saved = (AgentConfig) body.get("agent");
        assertEquals("openai", saved.getType());
        assertNotNull(saved.getId());
        verify(mockRepo).save(any(AgentConfig.class));
    }

    @Test
    @DisplayName("POST /api/agents/from-template/{type} with overrides applies name override")
    void testCreateFromTemplateWithOverrides() {
        when(mockRepo.save(any(AgentConfig.class))).thenAnswer(inv -> inv.getArgument(0));

        Map<String, Object> overrides = Map.of("name", "My Custom Agent");
        ResponseEntity<Map<String, Object>> resp = controller.createFromTemplate("claude", overrides);

        assertEquals(200, resp.getStatusCode().value());
        @SuppressWarnings("unchecked")
        AgentConfig saved = (AgentConfig) resp.getBody().get("agent");
        assertEquals("My Custom Agent", saved.getName());
    }

    @Test
    @DisplayName("POST /api/agents/from-template/{type} with unknown type returns 400")
    void testCreateFromTemplateUnknownType() {
        // Unknown type causes controller to skip save(), so no mock needed.
        // But we configure it anyway to avoid NPE in logger.info after save path.
        when(mockRepo.save(any(AgentConfig.class))).thenAnswer(inv -> {
            AgentConfig c = inv.getArgument(0);
            c.setId("fallback-id");
            return c;
        });

        ResponseEntity<Map<String, Object>> resp = controller.createFromTemplate("unknown", null);

        assertEquals(400, resp.getStatusCode().value());
        Map<String, Object> body = resp.getBody();
        assertEquals(false, body.get("success"));
        assertTrue(body.get("error").toString().contains("未知"));
        verify(mockRepo, never()).save(any());
    }

    @Test
    @DisplayName("POST /api/agents/from-template/{type} resets ID to null for new record")
    void testCreateFromTemplateResetsId() {
        when(mockRepo.save(any(AgentConfig.class))).thenAnswer(inv -> {
            AgentConfig c = inv.getArgument(0);
            // ID should be null so save() generates a new one
            return c;
        });

        controller.createFromTemplate("http", null);

        verify(mockRepo).save(argThat(c -> c.getId() == null || c.getId().isEmpty()));
    }

    @Test
    @DisplayName("POST /api/agents/from-template/{type} applies config override")
    void testCreateFromTemplateConfigOverride() {
        when(mockRepo.save(any(AgentConfig.class))).thenAnswer(inv -> {
            AgentConfig c = inv.getArgument(0);
            c.setId("new-id-from-mock");
            return c;
        });

        Map<String, Object> configOverride = new HashMap<>();
        configOverride.put("model", "gpt-4");
        Map<String, Object> overrides = new HashMap<>();
        overrides.put("name", "GPT-4 Agent");
        overrides.put("config", configOverride);

        ResponseEntity<Map<String, Object>> resp = controller.createFromTemplate("openai", overrides);

        assertEquals(200, resp.getStatusCode().value());
        Map<String, Object> body = resp.getBody();
        assertNotNull(body, "response body should not be null");
        assertEquals(true, body.get("success"));
        assertNotNull(body.get("agent"), "agent in response should not be null");
        @SuppressWarnings("unchecked")
        AgentConfig saved = (AgentConfig) body.get("agent");
        assertEquals("GPT-4 Agent", saved.getName());
        assertEquals("gpt-4", saved.getConfig().get("model"));
    }

    // ============================================================
    // POST /api/agents/{id}/test — Test agent
    // ============================================================

    @Test
    @DisplayName("POST /api/agents/{id}/test returns success when agent executes")
    void testTestAgentSuccess() {
        AgentConfig agent = makeConfig("id1", "Demo", "http", "https://example.com");
        Agent mockAgent = input -> "Demo response for: " + input;
        when(mockRepo.findById("id1")).thenReturn(Optional.of(agent));
        when(mockFactory.createAgent(agent)).thenReturn(mockAgent);

        ResponseEntity<Map<String, Object>> resp = controller.testAgent("id1", Map.of("input", "Hello"));

        assertEquals(200, resp.getStatusCode().value());
        Map<String, Object> body = resp.getBody();
        assertEquals(true, body.get("success"));
        assertEquals("Hello", body.get("input"));
        assertEquals("Demo response for: Hello", body.get("output"));
        assertNotNull(body.get("responseTimeMs"));
    }

    @Test
    @DisplayName("POST /api/agents/{id}/test returns error when agent throws exception")
    void testTestAgentException() {
        AgentConfig agent = makeConfig("id1", "Failing", "http", "https://example.com");
        Agent mockAgent = input -> { throw new RuntimeException("Network error"); };
        when(mockRepo.findById("id1")).thenReturn(Optional.of(agent));
        when(mockFactory.createAgent(agent)).thenReturn(mockAgent);

        ResponseEntity<Map<String, Object>> resp = controller.testAgent("id1", Map.of("input", "Hello"));

        assertEquals(200, resp.getStatusCode().value());
        Map<String, Object> body = resp.getBody();
        assertEquals(false, body.get("success"));
        assertEquals("Hello", body.get("input"));
        assertTrue(body.get("error").toString().contains("Network error"));
        assertTrue(body.get("message").toString().contains("Network error"));
    }

    @Test
    @DisplayName("POST /api/agents/{id}/test uses default input when input missing")
    void testTestAgentDefaultInput() {
        AgentConfig agent = makeConfig("id1", "Demo", "http", "https://example.com");
        Agent mockAgent = input -> "Got: " + input;
        when(mockRepo.findById("id1")).thenReturn(Optional.of(agent));
        when(mockFactory.createAgent(agent)).thenReturn(mockAgent);

        ResponseEntity<Map<String, Object>> resp = controller.testAgent("id1", Map.of());

        assertEquals(200, resp.getStatusCode().value());
        Map<String, Object> body = resp.getBody();
        assertEquals(true, body.get("success"));
        assertEquals("测试输入", body.get("input"));
    }

    @Test
    @DisplayName("POST /api/agents/{id}/test returns 404 when agent not found")
    void testTestAgentNotFound() {
        when(mockRepo.findById("not-exist")).thenReturn(Optional.empty());

        ResponseEntity<Map<String, Object>> resp = controller.testAgent("not-exist", Map.of("input", "test"));

        assertEquals(404, resp.getStatusCode().value());
        Map<String, Object> body = resp.getBody();
        assertEquals(false, body.get("success"));
        assertEquals("Agent配置不存在", body.get("error"));
    }

    // ============================================================
    // Edge cases
    // ============================================================

    @Test
    @DisplayName("Agent names with special characters are preserved")
    void testCreateAgentSpecialCharsInName() {
        AgentConfig input = makeConfig(null, "Agent <script>alert(1)</script>", "http", "https://example.com");
        when(mockRepo.save(any(AgentConfig.class))).thenAnswer(inv -> {
            AgentConfig c = inv.getArgument(0);
            c.setId("id");
            return c;
        });

        ResponseEntity<Map<String, Object>> resp = controller.createAgent(input);

        assertEquals(200, resp.getStatusCode().value());
        // Backend does not sanitize — sanitization is frontend responsibility
        assertEquals("Agent <script>alert(1)</script>", input.getName());
    }

    @Test
    @DisplayName("Agent endpoints with query params are preserved")
    void testCreateAgentEndpointWithQueryParams() {
        AgentConfig input = makeConfig(null, "Agent", "http", "https://api.example.com?api_version=v2&env=prod");
        when(mockRepo.save(any(AgentConfig.class))).thenAnswer(inv -> {
            AgentConfig c = inv.getArgument(0);
            c.setId("id");
            return c;
        });

        ResponseEntity<Map<String, Object>> resp = controller.createAgent(input);

        assertEquals(200, resp.getStatusCode().value());
        assertTrue(input.getEndpoint().contains("api_version=v2"));
    }

    @Test
    @DisplayName("Controller repository methods are called correctly")
    void testRepositoryInteractions() {
        // Verify that repository methods are called with correct arguments
        AgentConfig input = makeConfig(null, "Test", "openai", "https://api.openai.com");
        when(mockRepo.save(any())).thenAnswer(inv -> {
            AgentConfig c = inv.getArgument(0);
            c.setId("id1");
            return c;
        });

        controller.createAgent(input);
        verify(mockRepo).save(input);
        verify(mockRepo, never()).findAll();
        verify(mockRepo, never()).deleteById(anyString());
    }
}
