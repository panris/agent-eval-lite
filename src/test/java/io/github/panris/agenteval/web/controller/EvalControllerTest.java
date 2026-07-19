package io.github.panris.agenteval.web.controller;

import io.github.panris.agenteval.repository.TestCaseRepository;
import io.github.panris.agenteval.repository.AgentConfigRepository;
import io.github.panris.agenteval.service.AsyncEvalService;
import io.github.panris.agenteval.service.ReportService;
import io.github.panris.agenteval.agent.AgentFactory;
import org.junit.jupiter.api.*;
import org.springframework.ui.Model;

import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.mockito.Mockito.anyBoolean;
import static org.mockito.Mockito.doReturn;
import org.mockito.ArgumentMatchers;

/**
 * Pure unit tests for EvalController — no Spring context needed.
 * Controller is constructed directly with mocked dependencies.
 */
class EvalControllerTest {

    private EvalController controller;
    private AsyncEvalService mockAsyncEvalService;
    private ReportService mockReportService;
    private TestCaseRepository mockTestCaseRepository;
    private AgentConfigRepository mockAgentConfigRepository;
    private AgentFactory mockAgentFactory;

    @BeforeEach
    void setUp() {
        mockAsyncEvalService = mock(AsyncEvalService.class);
        mockReportService = mock(ReportService.class);
        mockTestCaseRepository = mock(TestCaseRepository.class);
        mockAgentFactory = mock(AgentFactory.class);
        mockAgentConfigRepository = mock(AgentConfigRepository.class);
        ExecutorService mockExecutor = Executors.newSingleThreadExecutor();
        controller = new EvalController(
                mockTestCaseRepository, mockAgentConfigRepository, mockAsyncEvalService, 
                mockReportService, mockAgentFactory, mockExecutor);
    }

    // ============ Page routes ============

    @Test
    @DisplayName("GET / → returns 'index' view name")
    void testIndex() {
        Model mockModel = mock(Model.class);
        String view = controller.index(mockModel);
        assertEquals("index", view);
        verify(mockModel).addAttribute(eq("testCases"), any());
        verify(mockModel).addAttribute(eq("metrics"), any());
    }

    @Test
    @DisplayName("GET /manage → returns 'manage' view name")
    void testManage() {
        assertEquals("manage", controller.manage());
    }

    // ============ POST /api/evaluate (sync) — parameter validation ============

    @Test
    @DisplayName("POST /api/evaluate with null request → BAD_REQUEST")
    void testEvaluateWithNullRequest() {
        Map<String, Object> resp = controller.evaluate(null, null);
        assertFalse((Boolean) resp.get("success"));
        assertNotNull(resp.get("error"));
    }

    @Test
    @DisplayName("POST /api/evaluate with empty testCases → BAD_REQUEST")
    void testEvaluateWithEmptyTestCases() {
        EvalRequest req = new EvalRequest();
        req.setTestCases(List.of());

        Map<String, Object> resp = controller.evaluate(req, null);

        assertFalse((Boolean) resp.get("success"));
        assertNotNull(resp.get("error"));
    }

    @Test
    @DisplayName("POST /api/evaluate with >100 testCases → BAD_REQUEST")
    void testEvaluateWithTooManyTestCases() {
        List<TestCaseDto> cases = new ArrayList<>();
        for (int i = 0; i < 101; i++) {
            TestCaseDto dto = new TestCaseDto();
            dto.setInput("in" + i);
            dto.setExpected("out" + i);
            cases.add(dto);
        }
        EvalRequest req = new EvalRequest();
        req.setTestCases(cases);
        req.setMetrics(List.of("correctness"));

        Map<String, Object> resp = controller.evaluate(req, null);

        assertFalse((Boolean) resp.get("success"));
        assertTrue(((String) resp.get("error")).contains("100"));
    }

    @Test
    @DisplayName("POST /api/evaluate with invalid metric → BAD_REQUEST")
    void testEvaluateWithInvalidMetric() {
        EvalRequest req = new EvalRequest();
        TestCaseDto dto = new TestCaseDto();
        dto.setInput("hello");
        dto.setExpected("world");
        req.setTestCases(List.of(dto));
        req.setMetrics(List.of("not_a_real_metric"));

        Map<String, Object> resp = controller.evaluate(req, null);

        assertFalse((Boolean) resp.get("success"));
        assertNotNull(resp.get("error"));
    }

    // ============ POST /api/evaluate/cases ============

    @Test
    @DisplayName("POST /api/evaluate/cases with empty caseIds → BAD_REQUEST")
    void testEvaluateByCaseIdsEmpty() {
        EvaluateByCaseIdsRequest req = new EvaluateByCaseIdsRequest();
        req.setCaseIds(List.of());

        Map<String, Object> resp = controller.evaluateByCaseIds(req);

        assertFalse((Boolean) resp.get("success"));
        assertEquals("测试用例 ID 列表不能为空", resp.get("error"));
    }

    @Test
    @DisplayName("POST /api/evaluate/cases with >100 IDs → BAD_REQUEST")
    void testEvaluateByCaseIdsTooMany() {
        List<String> ids = new ArrayList<>();
        for (int i = 0; i < 101; i++) ids.add("id-" + i);

        EvaluateByCaseIdsRequest req = new EvaluateByCaseIdsRequest();
        req.setCaseIds(ids);
        req.setMetrics(List.of("correctness"));

        Map<String, Object> resp = controller.evaluateByCaseIds(req);

        assertFalse((Boolean) resp.get("success"));
        assertTrue(((String) resp.get("error")).contains("100"));
    }

    // ============ POST /api/evaluate/async ============

    @Test
    @DisplayName("POST /api/evaluate/async with valid request → returns taskId PENDING")
    void testAsyncEvaluate() {
        when(mockAsyncEvalService.submitTask(
                anyList(), anyList(), anyString(),
                eq(300), any(), any(), any(), any()
        )).thenReturn("task-abc123");

        EvalRequest req = new EvalRequest();
        TestCaseDto dto = new TestCaseDto();
        dto.setInput("hello");
        dto.setExpected("world");
        req.setTestCases(List.of(dto));
        req.setMetrics(List.of("correctness"));
        req.setAgentType("demo");

        Map<String, Object> resp = controller.evaluateAsync(req);

        assertTrue((Boolean) resp.get("success"));
        assertEquals("task-abc123", resp.get("taskId"));
        assertEquals("PENDING", resp.get("status"));
    }

    @Test
    @DisplayName("POST /api/evaluate/async with invalid metric → BAD_REQUEST")
    void testAsyncEvaluateInvalidMetric() {
        EvalRequest req = new EvalRequest();
        TestCaseDto dto = new TestCaseDto();
        dto.setInput("hello");
        dto.setExpected("world");
        req.setTestCases(List.of(dto));
        req.setMetrics(List.of("invalid_metric"));

        Map<String, Object> resp = controller.evaluateAsync(req);

        assertFalse((Boolean) resp.get("success"));
        assertNotNull(resp.get("error"));
    }

    @Test
    @DisplayName("POST /api/evaluate/async with >100 testCases → BAD_REQUEST")
    void testAsyncEvaluateTooManyTestCases() {
        List<TestCaseDto> cases = new ArrayList<>();
        for (int i = 0; i < 101; i++) {
            TestCaseDto dto = new TestCaseDto();
            dto.setInput("in" + i);
            dto.setExpected("out" + i);
            cases.add(dto);
        }
        EvalRequest req = new EvalRequest();
        req.setTestCases(cases);
        req.setMetrics(List.of("correctness"));

        Map<String, Object> resp = controller.evaluateAsync(req);

        assertFalse((Boolean) resp.get("success"));
        assertTrue(((String) resp.get("error")).contains("100"));
    }

    // ============ GET /api/tasks ============

    @Test
    @DisplayName("GET /api/tasks → returns list of task statuses")
    void testListTasks() {
        AsyncEvalService.TaskStatus s1 = new AsyncEvalService.TaskStatus("task-1");
        s1.status = "COMPLETED";
        s1.totalCases = 2;
        s1.completedCases = 2;
        s1.createdAt = System.currentTimeMillis();

        AsyncEvalService.TaskStatus s2 = new AsyncEvalService.TaskStatus("task-2");
        s2.status = "RUNNING";
        s2.totalCases = 3;
        s2.completedCases = 1;
        s2.createdAt = System.currentTimeMillis();

        when(mockAsyncEvalService.getAllStatuses()).thenReturn(List.of(s1, s2));

        List<Map<String, Object>> tasks = controller.listTasks();

        assertEquals(2, tasks.size());
        assertEquals("task-1", tasks.get(0).get("taskId"));
        assertEquals("COMPLETED", tasks.get(0).get("status"));
        assertEquals("task-2", tasks.get(1).get("taskId"));
        assertEquals("RUNNING", tasks.get(1).get("status"));
    }

    @Test
    @DisplayName("GET /api/tasks/{id} → returns specific task status")
    void testGetTaskStatus() {
        AsyncEvalService.TaskStatus s = new AsyncEvalService.TaskStatus("task-xyz");
        s.status = "PENDING";
        s.totalCases = 5;
        s.completedCases = 0;
        s.createdAt = System.currentTimeMillis();

        when(mockAsyncEvalService.getStatus("task-xyz")).thenReturn(s);

        Map<String, Object> resp = controller.getTaskStatus("task-xyz");

        assertTrue((Boolean) resp.get("success"));
        assertEquals("task-xyz", resp.get("taskId"));
        assertEquals("PENDING", resp.get("status"));
    }

    @Test
    @DisplayName("GET /api/tasks/{id} with unknown id → returns error")
    void testGetTaskStatusNotFound() {
        when(mockAsyncEvalService.getStatus("unknown")).thenReturn(null);

        Map<String, Object> resp = controller.getTaskStatus("unknown");

        assertFalse((Boolean) resp.get("success"));
        assertEquals("任务不存在", resp.get("error"));
    }

    // ============ GET /api/reports ============

    @Test
    @DisplayName("GET /api/reports → returns paginated report list")
    void testGetReports() {
        Map<String, Object> report = new LinkedHashMap<>();
        report.put("id", "report-001");
        report.put("group", "default");
        report.put("pass_rate", 100.0);

        doReturn(Map.of(
                "reports", List.of(report),
                "total", 1, "filtered", 1, "page", 1, "size", 20, "totalPages", 1
        )).when(mockReportService).getAllReports(
                anyString(),
                (Long) any(), (Long) any(),
                (String) any(), (String) any(), (String) any(), (String) any(),
                (Boolean) any(),
                (String) any(), (String) any(), (String) any(),
                anyInt(), anyInt(), anyBoolean()
        );

        Map<String, Object> resp = controller.getReports(
                "desc", null, null, null, null, null, null, null, null, null, "time", 1, 20, false
        );

        assertNotNull(resp);
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> reports = (List<Map<String, Object>>) resp.get("reports");
        assertEquals(1, reports.size());
        assertEquals("report-001", reports.get(0).get("id"));
    }

    @Test
    @DisplayName("GET /api/reports?keyword=test → delegates keyword to service")
    void testGetReportsWithKeyword() {
        doReturn(Map.of(
                "reports", List.of(),
                "total", 0, "filtered", 0, "page", 1, "size", 20, "totalPages", 0
        )).when(mockReportService).getAllReports(
                anyString(),
                (Long) any(), (Long) any(),
                (String) any(), (String) any(), (String) any(), (String) any(),
                (Boolean) any(),
                (String) any(), eq("test"), (String) any(),
                anyInt(), anyInt(), anyBoolean()
        );

        Map<String, Object> resp = controller.getReports(
                "desc", null, null, null, null, null, null, null, null, "test", "time", 1, 20, false
        );

        assertNotNull(resp);
        verify(mockReportService).getAllReports(
                anyString(),
                (Long) any(), (Long) any(),
                (String) any(), (String) any(), (String) any(), (String) any(),
                (Boolean) any(),
                (String) any(), eq("test"), (String) any(),
                anyInt(), anyInt(), anyBoolean()
        );
    }

    // ============ GET /api/reports/favorites ============

    @Test
    @DisplayName("GET /api/reports/favorites → returns favorite reports")
    void testGetFavorites() {
        Map<String, Object> fav = new LinkedHashMap<>();
        fav.put("id", "fav-001");
        fav.put("favorite", true);

        when(mockReportService.getFavorites()).thenReturn(Map.of(
                "favorites", List.of(fav),
                "total", 1
        ));

        Map<String, Object> resp = controller.getFavorites();

        assertNotNull(resp);
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> favorites = (List<Map<String, Object>>) resp.get("favorites");
        assertEquals(1, favorites.size());
        assertEquals("fav-001", favorites.get(0).get("id"));
    }

    // ============ GET /api/reports/{id} ============

    @Test
    @DisplayName("GET /api/reports/{id} → returns report details")
    void testGetReport() {
        Map<String, Object> report = new LinkedHashMap<>();
        report.put("id", "report-xyz");
        report.put("totalTestCases", 3);
        report.put("summary", Map.of("passed_test_cases", 2));

        when(mockReportService.getReport("report-xyz")).thenReturn(report);

        Map<String, Object> resp = controller.getReport("report-xyz");

        assertTrue((Boolean) resp.get("success"));
        assertEquals("report-xyz", resp.get("reportId"));
        assertNotNull(resp.get("summary"));
    }

    @Test
    @DisplayName("GET /api/reports/{id} with unknown id → returns NOT_FOUND")
    void testGetReportNotFound() {
        when(mockReportService.getReport("ghost")).thenReturn(null);

        Map<String, Object> resp = controller.getReport("ghost");

        assertFalse((Boolean) resp.get("success"));
        assertEquals("报告不存在", resp.get("error"));
    }

    // ============ DELETE /api/reports/{id} ============

    @Test
    @DisplayName("DELETE /api/reports/{id} → returns success")
    void testDeleteReport() {
        when(mockReportService.deleteReport("to-delete")).thenReturn(Map.of("success", true, "message", "报告已删除"));

        Map<String, Object> resp = controller.deleteReport("to-delete");

        assertTrue((Boolean) resp.get("success"));
        verify(mockReportService).deleteReport("to-delete");
    }

    @Test
    @DisplayName("DELETE /api/reports/{id} not found → returns NOT_FOUND")
    void testDeleteReportNotFound() {
        when(mockReportService.deleteReport("ghost")).thenReturn(Map.of("success", false, "error", "报告不存在"));

        Map<String, Object> resp = controller.deleteReport("ghost");

        assertFalse((Boolean) resp.get("success"));
    }

    // ============ POST /api/reports/{id}/copy ============

    @Test
    @DisplayName("POST /api/reports/{id}/copy → returns new report ID")
    void testCopyReport() {
        when(mockReportService.copyReport("orig-001")).thenReturn(Map.of(
                "success", true, "newId", "copy-001", "message", "报告已复制"
        ));

        Map<String, Object> resp = controller.copyReport("orig-001");

        assertTrue((Boolean) resp.get("success"));
        assertEquals("copy-001", resp.get("newId"));
    }

    // ============ POST /api/reports/{id}/favorite ============

    @Test
    @DisplayName("POST /api/reports/{id}/favorite → toggles and returns new state")
    void testToggleFavorite() {
        when(mockReportService.toggleFavorite("report-001")).thenReturn(Map.of(
                "success", true, "favorite", true
        ));

        Map<String, Object> resp = controller.toggleFavorite("report-001");

        assertTrue((Boolean) resp.get("success"));
        assertEquals(Boolean.TRUE, resp.get("favorite"));
    }

    // ============ GET /api/reports/compare ============

    @Test
    @DisplayName("GET /api/reports/compare with valid IDs → returns comparison")
    void testCompareReports() {
        when(mockReportService.compareReports(List.of("r1", "r2")))
                .thenReturn(Map.of(
                        "count", 2,
                        "reports", List.of(Map.of("id", "r1"), Map.of("id", "r2")),
                        "passRateStats", Map.of("min", 60.0, "max", 80.0, "avg", 70.0)
                ));

        Map<String, Object> resp = controller.compareReports("r1,r2", "correctness");

        assertNotNull(resp);
        assertEquals(2, resp.get("count"));
        @SuppressWarnings("unchecked")
        Map<String, Double> stats = (Map<String, Double>) resp.get("passRateStats");
        assertEquals(70.0, stats.get("avg"));
    }

    @Test
    @DisplayName("GET /api/reports/compare with <2 IDs → returns BAD_REQUEST")
    void testCompareReportsTooFewIds() {
        Map<String, Object> resp = controller.compareReports("only-one", null);

        assertFalse((Boolean) resp.get("success"));
    }
}
