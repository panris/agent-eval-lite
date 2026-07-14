package io.github.panris.agenteval.web.controller;

import io.github.panris.agenteval.service.ReportService;
import org.junit.jupiter.api.*;
import org.springframework.ui.Model;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Pure unit tests for ShareController — no Spring context needed.
 */
class ShareControllerTest {

    private ShareController controller;
    private ReportService mockReportService;

    @BeforeEach
    void setUp() {
        mockReportService = mock(ReportService.class);
        controller = new ShareController(mockReportService);
    }

    // ============ GET /share/{shareId} ============

    @Test
    @DisplayName("GET /share/{shareId} with unknown shareId → redirects to /")
    void testShareUnknownShareId() {
        when(mockReportService.resolveShareId("unknown-share")).thenReturn(null);

        String view = controller.showSharedReport("unknown-share", mock(Model.class));

        assertEquals("redirect:/", view);
    }

    @Test
    @DisplayName("GET /share/{shareId} with valid shareId but report is null → redirects to /")
    void testShareValidIdButNullReport() {
        when(mockReportService.resolveShareId("valid-share")).thenReturn("report-1");
        when(mockReportService.getReport("report-1")).thenReturn(null);

        String view = controller.showSharedReport("valid-share", mock(Model.class));

        assertEquals("redirect:/", view);
    }

    @Test
    @DisplayName("GET /share/{shareId} with valid shareId and report → returns 'share' view")
    void testShareSuccess() {
        Map<String, Object> report = new LinkedHashMap<>();
        report.put("summary", Map.of("pass_rate", 85.0));
        report.put("timestamp", 1700000000L);
        report.put("totalTestCases", 10);
        report.put("passedTestCases", 8);
        report.put("failedTestCases", 2);
        report.put("evaluations", List.of(
            Map.of(
                "testCaseName", "case-1",
                "passed", true,
                "overallScore", 1.0,
                "agentOutput", Map.of("output", "hello world")
            )
        ));

        when(mockReportService.resolveShareId("valid-share")).thenReturn("report-1");
        when(mockReportService.getReport("report-1")).thenReturn(report);

        Model mockModel = mock(Model.class);
        String view = controller.showSharedReport("valid-share", mockModel);

        assertEquals("share", view);
        verify(mockModel).addAttribute(eq("reportId"), eq("report-1"));
        verify(mockModel).addAttribute(eq("summary"), any());
        verify(mockModel).addAttribute(eq("evaluations"), anyList());
    }

    @Test
    @DisplayName("GET /share/{shareId} with null evaluations → returns empty list")
    void testShareNullEvaluations() {
        Map<String, Object> report = new LinkedHashMap<>();
        report.put("summary", Map.of("pass_rate", 100.0));
        report.put("timestamp", 1700000000L);
        report.put("evaluations", null);
        report.put("totalTestCases", 0);
        report.put("passedTestCases", 0);
        report.put("failedTestCases", 0);

        when(mockReportService.resolveShareId("share-no-evals")).thenReturn("report-no-evals");
        when(mockReportService.getReport("report-no-evals")).thenReturn(report);

        Model mockModel = mock(Model.class);
        String view = controller.showSharedReport("share-no-evals", mockModel);

        assertEquals("share", view);
        verify(mockModel).addAttribute(eq("evaluations"), eq(List.of()));
    }
}
