package io.github.panris.agenteval.web.controller;

import io.github.panris.agenteval.service.ReportService;
import org.junit.jupiter.api.*;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Pure unit tests for ReportController — no Spring context needed.
 * Controller is constructed directly with mocked ReportService.
 */
class ReportControllerTest {

    private ReportController controller;
    private ReportService mockReportService;

    @BeforeEach
    void setUp() {
        mockReportService = mock(ReportService.class);
        controller = new ReportController(mockReportService);
    }

    // ============ GET /api/reports ============

    @Test
    @DisplayName("GET /api/reports → returns paginated report list")
    void testGetReports() {
        Map<String, Object> report = new java.util.LinkedHashMap<>();
        report.put("id", "report-001");
        report.put("group", "default");
        report.put("pass_rate", 100.0);

        when(mockReportService.getAllReports(
                anyString(),
                any(), any(),
                any(), any(), any(), any(),
                any(),
                any(), any(), any(),
                anyInt(), anyInt(), anyBoolean()
        )).thenReturn(Map.of(
                "reports", List.of(report),
                "total", 1, "filtered", 1, "page", 1, "size", 20, "totalPages", 1
        ));

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
        when(mockReportService.getAllReports(
                anyString(),
                any(), any(),
                any(), any(), any(), any(),
                any(),
                any(), eq("test"), any(),
                anyInt(), anyInt(), anyBoolean()
        )).thenReturn(Map.of(
                "reports", List.of(),
                "total", 0, "filtered", 0, "page", 1, "size", 20, "totalPages", 0
        ));

        Map<String, Object> resp = controller.getReports(
                "desc", null, null, null, null, null, null, null, null, "test", "time", 1, 20, false
        );

        assertNotNull(resp);
        verify(mockReportService).getAllReports(
                anyString(),
                any(), any(),
                any(), any(), any(), any(),
                any(),
                any(), eq("test"), any(),
                anyInt(), anyInt(), anyBoolean()
        );
    }

    // ============ GET /api/reports/favorites ============

    @Test
    @DisplayName("GET /api/reports/favorites → returns favorite reports")
    void testGetFavorites() {
        Map<String, Object> fav = new java.util.LinkedHashMap<>();
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
        Map<String, Object> report = new java.util.LinkedHashMap<>();
        report.put("id", "report-xyz");
        report.put("totalTestCases", 3);
        report.put("summary", Map.of("passed_test_cases", 2));

        when(mockReportService.getReport("report-xyz")).thenReturn(report);

        Map<String, Object> resp = controller.getReport("report-xyz");

        assertTrue((Boolean) resp.get("success"));
        assertEquals("report-xyz", resp.get("id"));
    }

    @Test
    @DisplayName("GET /api/reports/{id} with unknown id → returns error")
    void testGetReportNotFound() {
        when(mockReportService.getReport("ghost")).thenReturn(null);

        Map<String, Object> resp = controller.getReport("ghost");

        assertFalse((Boolean) resp.get("success"));
        assertEquals("报告不存在: ghost", resp.get("error"));
    }

    // ============ DELETE /api/reports/{id} ============

    @Test
    @DisplayName("DELETE /api/reports/{id} → calls service and returns success")
    void testDeleteReport() {
        Map<String, Object> svcResp = Map.of("success", true, "message", "报告已删除");
        when(mockReportService.deleteReport("to-delete")).thenReturn(svcResp);

        Map<String, Object> resp = controller.deleteReport("to-delete");

        assertTrue((Boolean) resp.get("success"));
        verify(mockReportService).deleteReport("to-delete");
    }

    @Test
    @DisplayName("DELETE /api/reports/{id} not found → returns error")
    void testDeleteReportNotFound() {
        when(mockReportService.deleteReport("ghost")).thenReturn(
                Map.of("success", false, "error", "报告不存在")
        );

        Map<String, Object> resp = controller.deleteReport("ghost");

        assertFalse((Boolean) resp.get("success"));
    }

    // ============ DELETE /api/reports (clear all) ============

    @Test
    @DisplayName("DELETE /api/reports without confirm → returns BAD_REQUEST")
    void testClearAllReportsWithoutConfirm() {
        Map<String, Object> resp = controller.clearAllReports(Map.of("confirm", "false"));

        assertFalse((Boolean) resp.get("success"));
        assertEquals("请在请求体中传入 confirm=true 确认清空", resp.get("error"));
    }

    @Test
    @DisplayName("DELETE /api/reports with confirm=true → clears all reports")
    void testClearAllReportsWithConfirm() {
        when(mockReportService.clearAllReports()).thenReturn(Map.of("success", true, "cleared", true));

        Map<String, Object> resp = controller.clearAllReports(Map.of("confirm", "true"));

        assertTrue((Boolean) resp.get("success"));
        verify(mockReportService).clearAllReports();
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

    // ============ POST /api/reports/{id}/share ============

    @Test
    @DisplayName("POST /api/reports/{id}/share → returns share link")
    void testShareReport() {
        when(mockReportService.createShareLink("report-001")).thenReturn(Map.of(
                "success", true, "shareId", "abc123", "url", "/share/abc123"
        ));

        Map<String, Object> resp = controller.shareReport("report-001");

        assertTrue((Boolean) resp.get("success"));
        assertEquals("abc123", resp.get("shareId"));
    }

    // ============ PUT /api/reports/{id}/tags ============

    @Test
    @DisplayName("PUT /api/reports/{id}/tags → updates tags")
    void testUpdateTags() {
        when(mockReportService.updateTags(eq("report-001"), anyList()))
                .thenReturn(Map.of("success", true, "tags", List.of("tag1", "tag2")));

        Map<String, Object> resp = controller.updateTags("report-001",
                Map.of("tags", List.of("tag1", "tag2")));

        assertTrue((Boolean) resp.get("success"));
    }

    // ============ PUT /api/reports/{id}/note ============

    @Test
    @DisplayName("PUT /api/reports/{id}/note → updates note")
    void testUpdateNote() {
        when(mockReportService.updateNote("report-001", "new note"))
                .thenReturn(Map.of("success", true, "note", "new note"));

        Map<String, Object> resp = controller.updateNote("report-001",
                Map.of("note", "new note"));

        assertTrue((Boolean) resp.get("success"));
    }

    // ============ GET /api/reports/compare ============

    @Test
    @DisplayName("GET /api/reports/compare with valid IDs → returns comparison")
    void testCompareReports() {
        when(mockReportService.compareReports(anyList()))
                .thenReturn(Map.of(
                        "count", 2,
                        "reports", List.of(Map.of("id", "r1"), Map.of("id", "r2")),
                        "passRateStats", Map.of("min", 60.0, "max", 80.0, "avg", 70.0)
                ));

        Map<String, Object> resp = controller.compareReports("r1,r2", "correctness");

        assertNotNull(resp);
        assertEquals(2, resp.get("count"));
    }

    @Test
    @DisplayName("GET /api/reports/compare with <2 IDs → returns BAD_REQUEST")
    void testCompareReportsTooFewIds() {
        Map<String, Object> resp = controller.compareReports("only-one", null);

        assertFalse((Boolean) resp.get("success"));
    }

    // ============ GET /api/reports/{id}/export ============

    @Test
    @DisplayName("GET /api/reports/{id}/export with unknown id → NOT_FOUND")
    void testExportReportNotFound() {
        when(mockReportService.getReport("ghost")).thenReturn(null);

        var resp = controller.exportReport("ghost", "json");

        assertEquals(org.springframework.http.HttpStatus.NOT_FOUND, resp.getStatusCode());
    }

    @Test
    @DisplayName("GET /api/reports/{id}/export with unsupported format → BAD_REQUEST")
    void testExportReportUnsupportedFormat() {
        Map<String, Object> report = Map.of("id", "report-xyz", "summary", Map.of());
        when(mockReportService.getReport("report-xyz")).thenReturn(report);

        var resp = controller.exportReport("report-xyz", "pdf");

        assertEquals(org.springframework.http.HttpStatus.BAD_REQUEST, resp.getStatusCode());
    }
}
