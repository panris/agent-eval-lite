package io.github.panris.agenteval.web.controller;

import io.github.panris.agenteval.repository.TestCaseRepository;
import io.github.panris.agenteval.repository.AgentConfigRepository;
import io.github.panris.agenteval.web.dto.EvalRequest;
import io.github.panris.agenteval.repository.EvalLlmConfigRepository;
import io.github.panris.agenteval.repository.EvalModelRepository;
import io.github.panris.agenteval.repository.EvalDimensionConfigRepository;
import io.github.panris.agenteval.service.AsyncEvalService;
import io.github.panris.agenteval.service.EvalCaseService;
import io.github.panris.agenteval.service.ReportService;
import io.github.panris.agenteval.service.EvalDimensionService;
import io.github.panris.agenteval.agent.AgentFactory;
import org.junit.jupiter.api.*;
import org.springframework.ui.Model;

import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Pure unit tests for EvalController — no Spring context needed.
 * Controller is constructed directly with mocked dependencies.
 *
 * EvalCaseService is used for real (backed by a mocked TestCaseRepository) so the
 * case-resolution and metric-validation logic runs end-to-end; only the async task
 * submission and agent layers are mocked.
 */
class EvalControllerTest {

    private EvalController controller;
    private AsyncEvalService mockAsyncEvalService;
    private ReportService mockReportService;
    private EvalCaseService evalCaseService;          // real service, mocked repo
    private TestCaseRepository mockTestCaseRepository;
    private AgentConfigRepository mockAgentConfigRepository;
    private AgentFactory mockAgentFactory;

    /** Minimal DTO impl for inline test cases. */
    static class Dto implements EvalCaseService.TestCaseDtoLike {
        private final String input;
        private final String expected;
        Dto(String i, String e) { this.input = i; this.expected = e; }
        public String getInput() { return input; }
        public String getExpected() { return expected; }
    }

    @BeforeEach
    void setUp() {
        mockAsyncEvalService = mock(AsyncEvalService.class);
        mockReportService = mock(ReportService.class);
        mockTestCaseRepository = mock(TestCaseRepository.class);
        mockAgentFactory = mock(AgentFactory.class);
        mockAgentConfigRepository = mock(AgentConfigRepository.class);
        EvalLlmConfigRepository mockLlmRepo = mock(EvalLlmConfigRepository.class);
        ExecutorService mockExecutor = Executors.newSingleThreadExecutor();
        EvalModelRepository mockEvalModelRepo = mock(EvalModelRepository.class);
        EvalDimensionConfigRepository mockDimConfigRepo = mock(EvalDimensionConfigRepository.class);
        EvalDimensionService evalDimensionService = new EvalDimensionService(mockDimConfigRepo, mockEvalModelRepo);
        evalCaseService = new EvalCaseService(mockTestCaseRepository);
        controller = new EvalController(
                evalCaseService, mockLlmRepo, mockAgentConfigRepository, mockAsyncEvalService,
                mockReportService, mockAgentFactory, evalDimensionService, mockExecutor);
    }

    // ============ Page routes ============

    @Test
    @DisplayName("GET / → returns 'index' view name")
    void testIndex() {
        when(mockReportService.getDashboardStats()).thenReturn(
                Map.of("totalReports", 0L, "avgPassRate", 0.0, "avgResponseTime", 0.0));
        Model mockModel = mock(Model.class);
        String view = controller.index(mockModel);
        assertEquals("index", view);
    }

    @Test
    @DisplayName("GET /manage → returns 'manage' view name")
    void testManage() {
        assertEquals("manage", controller.manage());
    }

    // ============ POST /api/evaluate (sync) — validation ============

    @Test
    @DisplayName("POST /api/evaluate with null request → error")
    void testEvaluateWithNullRequest() {
        Map<String, Object> resp = controller.evaluate(null);
        assertFalse((Boolean) resp.get("success"));
        assertNotNull(resp.get("error"));
    }

    @Test
    @DisplayName("POST /api/evaluate with null metrics → BAD_REQUEST")
    void testEvaluateWithNullMetrics() {
        EvalRequest req = new EvalRequest();
        req.setCases(List.of(new Dto("hello", "world")));

        Map<String, Object> resp = controller.evaluate(req);

        assertFalse((Boolean) resp.get("success"));
        assertTrue(((String) resp.get("error")).contains("指标"));
    }

    @Test
    @DisplayName("POST /api/evaluate with empty metric name → BAD_REQUEST")
    void testEvaluateWithEmptyMetricName() {
        EvalRequest req = new EvalRequest();
        req.setCases(List.of(new Dto("hello", "world")));
        req.setMetrics(List.of(""));

        Map<String, Object> resp = controller.evaluate(req);

        assertFalse((Boolean) resp.get("success"));
        assertTrue(((String) resp.get("error")).contains("指标"));
    }

    @Test
    @DisplayName("POST /api/evaluate with >100 testCases → BAD_REQUEST")
    void testEvaluateWithTooManyTestCases() {
        List<EvalCaseService.TestCaseDtoLike> cases = new ArrayList<>();
        for (int i = 0; i < 101; i++) cases.add(new Dto("in" + i, "out" + i));
        EvalRequest req = new EvalRequest();
        req.setCases(cases);
        req.setMetrics(List.of("correctness"));

        Map<String, Object> resp = controller.evaluate(req);

        assertFalse((Boolean) resp.get("success"));
        assertTrue(((String) resp.get("error")).contains("100"));
    }

    @Test
    @DisplayName("POST /api/evaluate with no cases/dimensions → BAD_REQUEST")
    void testEvaluateWithNoCasesOrDimensions() {
        EvalRequest req = new EvalRequest();
        req.setMetrics(List.of("correctness"));

        Map<String, Object> resp = controller.evaluate(req);

        assertFalse((Boolean) resp.get("success"));
        assertNotNull(resp.get("error"));
    }

    // ============ POST /api/evaluate/cases ============

    @Test
    @DisplayName("POST /api/evaluate/cases with empty caseIds → BAD_REQUEST")
    void testEvaluateByCaseIdsEmpty() {
        EvalRequest req = new EvalRequest();
        req.setCaseIds(List.of());
        req.setMetrics(List.of("correctness"));

        Map<String, Object> resp = controller.evaluateByCaseIds(req);

        assertFalse((Boolean) resp.get("success"));
        assertNotNull(resp.get("error"));
    }

    @Test
    @DisplayName("POST /api/evaluate/cases with >100 IDs → BAD_REQUEST")
    void testEvaluateByCaseIdsTooMany() {
        List<String> ids = new ArrayList<>();
        for (int i = 0; i < 101; i++) ids.add("id-" + i);
        EvalRequest req = new EvalRequest();
        req.setCaseIds(ids);
        req.setMetrics(List.of("correctness"));

        Map<String, Object> resp = controller.evaluateByCaseIds(req);

        assertFalse((Boolean) resp.get("success"));
        assertTrue(((String) resp.get("error")).contains("100"));
    }

    // ============ POST /api/evaluate/dimensions ============

    @Test
    @DisplayName("POST /api/evaluate/dimensions with no matching cases → BAD_REQUEST")
    void testEvaluateByDimensionsNoMatch() {
        EvalRequest req = new EvalRequest();
        req.setProject("nonexistent-project");
        req.setMetrics(List.of("correctness"));

        Map<String, Object> resp = controller.evaluateByDimensions(req);

        assertFalse((Boolean) resp.get("success"));
        assertNotNull(resp.get("error"));
    }

    // ============ POST /api/evaluate/async ============

    @Test
    @DisplayName("POST /api/evaluate/async with valid request → returns taskId")
    void testAsyncEvaluate() {
        when(mockAsyncEvalService.submitTask(
                anyList(), anyList(), anyString(),
                eq(300), any(), any(), any(), any(), any(), any()
        )).thenReturn("task-abc123");
        when(mockAsyncEvalService.getStatus("task-abc123"))
                .thenReturn(new AsyncEvalService.TaskStatus("task-abc123"));

        EvalRequest req = new EvalRequest();
        req.setCases(List.of(new Dto("hello", "world")));
        req.setMetrics(List.of("correctness"));
        req.setAgentType("demo");

        Map<String, Object> resp = controller.evaluateAsync(req);

        assertTrue((Boolean) resp.get("success"));
        assertEquals("task-abc123", resp.get("taskId"));
    }

    @Test
    @DisplayName("POST /api/evaluate/async with null metrics → BAD_REQUEST")
    void testAsyncEvaluateWithNullMetrics() {
        EvalRequest req = new EvalRequest();
        req.setCases(List.of(new Dto("hello", "world")));

        Map<String, Object> resp = controller.evaluateAsync(req);

        assertFalse((Boolean) resp.get("success"));
        assertTrue(((String) resp.get("error")).contains("指标"));
    }

    @Test
    @DisplayName("POST /api/evaluate/async with >100 testCases → BAD_REQUEST")
    void testAsyncEvaluateTooManyTestCases() {
        List<EvalCaseService.TestCaseDtoLike> cases = new ArrayList<>();
        for (int i = 0; i < 101; i++) cases.add(new Dto("in" + i, "out" + i));
        EvalRequest req = new EvalRequest();
        req.setCases(cases);
        req.setMetrics(List.of("correctness"));

        Map<String, Object> resp = controller.evaluateAsync(req);

        assertFalse((Boolean) resp.get("success"));
        assertTrue(((String) resp.get("error")).contains("100"));
    }
}
