package io.github.panris.agenteval.web.controller;

import io.github.panris.agenteval.repository.TestCaseRepository;
import io.github.panris.agenteval.service.ReportService;
import org.junit.jupiter.api.*;
import org.springframework.ui.Model;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Pure unit tests for HealthController — no Spring context needed.
 */
class HealthControllerTest {

    private HealthController controller;
    private TestCaseRepository mockRepository;
    private ReportService mockReportService;

    @BeforeEach
    void setUp() {
        mockRepository = mock(TestCaseRepository.class);
        mockReportService = mock(ReportService.class);
        controller = new HealthController(mockRepository, mockReportService);
    }

    // ============ GET /api/health ============

    @Test
    @DisplayName("GET /api/health returns UP status and metadata")
    void testHealthSuccess() {
        when(mockRepository.countAllTestCases()).thenReturn(42);
        when(mockReportService.getAllReports(
                anyString(), any(), any(), any(), any(), any(), any(), any(), any(), any(), anyString(), anyInt(), anyInt(), anyBoolean()
        )).thenReturn(Map.of("total", 10));

        Map<String, Object> resp = controller.health();

        assertEquals("UP", resp.get("status"));
        assertEquals("agent-eval-lite", resp.get("service"));
        assertNotNull(resp.get("version"));
        assertNotNull(resp.get("timestamp"));
        assertNotNull(resp.get("uptimeSeconds"));
        assertEquals(42, resp.get("testCases"));
        assertEquals(10, resp.get("reports"));
    }

    @Test
    @DisplayName("GET /api/health with zero counts → returns 0")
    void testHealthZeroCounts() {
        when(mockRepository.countAllTestCases()).thenReturn(0);
        when(mockReportService.getAllReports(
                anyString(), any(), any(), any(), any(), any(), any(), any(), any(), any(), anyString(), anyInt(), anyInt(), anyBoolean()
        )).thenReturn(Map.of("total", 0));

        Map<String, Object> resp = controller.health();

        assertEquals("UP", resp.get("status"));
        assertEquals(0, resp.get("testCases"));
        assertEquals(0, resp.get("reports"));
    }

    @Test
    @DisplayName("GET /api/health when repository throws → gracefully falls back to 0")
    void testHealthRepositoryError() {
        when(mockRepository.countAllTestCases()).thenThrow(new RuntimeException("disk error"));
        when(mockReportService.getAllReports(
                anyString(), any(), any(), any(), any(), any(), any(), any(), any(), any(), anyString(), anyInt(), anyInt(), anyBoolean()
        )).thenThrow(new RuntimeException("service error"));

        Map<String, Object> resp = controller.health();

        assertEquals("UP", resp.get("status")); // status is always UP
        assertEquals(0, resp.get("testCases")); // fallback
        assertEquals(0, resp.get("reports")); // fallback
    }

    @Test
    @DisplayName("GET /api/health includes correct version string")
    void testHealthVersion() {
        when(mockRepository.countAllTestCases()).thenReturn(0);
        when(mockReportService.getAllReports(
                anyString(), any(), any(), any(), any(), any(), any(), any(), any(), any(), anyString(), anyInt(), anyInt(), anyBoolean()
        )).thenReturn(Map.of("total", 0));

        Map<String, Object> resp = controller.health();

        assertEquals("0.1.0", resp.get("version"));
    }

    @Test
    @DisplayName("GET /api/health has positive uptime")
    void testHealthUptime() {
        when(mockRepository.countAllTestCases()).thenReturn(0);
        when(mockReportService.getAllReports(
                anyString(), any(), any(), any(), any(), any(), any(), any(), any(), any(), anyString(), anyInt(), anyInt(), anyBoolean()
        )).thenReturn(Map.of("total", 0));

        Map<String, Object> resp = controller.health();

        assertTrue((Long) resp.get("uptimeSeconds") >= 0);
    }
}
