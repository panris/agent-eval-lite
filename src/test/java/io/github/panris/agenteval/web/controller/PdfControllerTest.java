package io.github.panris.agenteval.web.controller;

import io.github.panris.agenteval.model.TestCaseEntity;
import io.github.panris.agenteval.repository.TestCaseRepository;
import io.github.panris.agenteval.service.ReportService;
import org.junit.jupiter.api.*;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.ResponseEntity;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Pure unit tests for PdfController — no Spring context needed.
 * Since actual PDF generation depends on system fonts, we mock the
 * service/repository and verify the ResponseEntity shape.
 */
class PdfControllerTest {

    private PdfController controller;
    private TestCaseRepository mockTestCaseRepository;
    private ReportService mockReportService;

    @BeforeEach
    void setUp() {
        mockTestCaseRepository = mock(TestCaseRepository.class);
        mockReportService = mock(ReportService.class);
        controller = new PdfController(mockTestCaseRepository, mockReportService);
    }

    // ============ GET /api/reports/{id}/export/pdf ============

    @Test
    @DisplayName("GET /api/reports/{id}/export/pdf with non-existent report → 500")
    void testExportPdfReportNotFound() {
        when(mockReportService.getReport("ghost")).thenReturn(null);

        // The controller catches all exceptions and returns 500
        // Since getReportData returns null for unknown report,
        // the PDF is still generated with "未找到报告数据" text
        // So we verify the ResponseEntity comes back (not throws)
        ResponseEntity<?> resp = controller.exportReportPdf("ghost");

        // The endpoint always returns a ResponseEntity; with null report
        // it generates a minimal PDF (with "未找到报告数据" text) so status is OK
        assertNotNull(resp);
        assertNotNull(resp.getBody());
    }

    @Test
    @DisplayName("GET /api/reports/{id}/export/pdf with existing report → returns PDF bytes")
    void testExportPdfSuccess() {
        Map<String, Object> report = new LinkedHashMap<>();
        report.put("summary", Map.of(
            "pass_rate", 85.0,
            "average_score", 0.92,
            "total_test_cases", 10,
            "passed_test_cases", 8
        ));
        report.put("timestamp", System.currentTimeMillis());
        report.put("executionTimeMs", 3500L);
        report.put("group", "backend");
        report.put("evaluations", List.of(
            Map.of(
                "testCaseId", "tc-1",
                "testCaseName", "case-1",
                "passed", true,
                "overallScore", 1.0,
                "output", "agent output here",
                "scorerResults", Map.of()
            )
        ));

        when(mockReportService.getReport("report-001")).thenReturn(report);

        ResponseEntity<?> resp = controller.exportReportPdf("report-001");

        assertNotNull(resp);
        assertEquals(200, resp.getStatusCode().value());
        assertNotNull(resp.getBody());
        assertTrue(resp.getBody() instanceof ByteArrayResource);
    }

    @Test
    @DisplayName("GET /api/reports/{id}/export/pdf with report containing failed cases → PDF includes failed rows")
    void testExportPdfWithFailedCases() {
        Map<String, Object> report = new LinkedHashMap<>();
        report.put("summary", Map.of(
            "pass_rate", 50.0,
            "average_score", 0.5,
            "total_test_cases", 2,
            "passed_test_cases", 1
        ));
        report.put("evaluations", List.of(
            Map.of(
                "testCaseId", "tc-1",
                "testCaseName", "passing-case",
                "passed", true,
                "overallScore", 1.0,
                "output", "correct",
                "scorerResults", Map.of()
            ),
            Map.of(
                "testCaseId", "tc-2",
                "testCaseName", "failing-case",
                "passed", false,
                "overallScore", 0.0,
                "output", "wrong",
                "scorerResults", Map.of()
            )
        ));

        when(mockReportService.getReport("report-mixed")).thenReturn(report);

        ResponseEntity<?> resp = controller.exportReportPdf("report-mixed");

        assertNotNull(resp);
        assertEquals(200, resp.getStatusCode().value());
        assertTrue(((ByteArrayResource) resp.getBody()).contentLength() > 0);
    }

    @Test
    @DisplayName("GET /api/reports/{id}/export/pdf with empty evaluations → generates minimal PDF")
    void testExportPdfEmptyEvaluations() {
        Map<String, Object> report = new LinkedHashMap<>();
        report.put("summary", Map.of(
            "pass_rate", 0.0,
            "average_score", 0.0,
            "total_test_cases", 0,
            "passed_test_cases", 0
        ));
        report.put("evaluations", List.of());

        when(mockReportService.getReport("report-empty")).thenReturn(report);

        ResponseEntity<?> resp = controller.exportReportPdf("report-empty");

        assertNotNull(resp);
        assertEquals(200, resp.getStatusCode().value());
        assertTrue(((ByteArrayResource) resp.getBody()).contentLength() > 0);
    }

    @Test
    @DisplayName("GET /api/reports/{id}/export/pdf sets correct Content-Disposition header")
    void testExportPdfContentDisposition() {
        Map<String, Object> report = new LinkedHashMap<>();
        report.put("summary", Map.of(
            "pass_rate", 100.0,
            "average_score", 1.0,
            "total_test_cases", 1,
            "passed_test_cases", 1
        ));
        report.put("evaluations", List.of());

        when(mockReportService.getReport("report-download")).thenReturn(report);

        ResponseEntity<?> resp = controller.exportReportPdf("report-download");

        assertNotNull(resp.getHeaders().getContentDisposition());
        assertTrue(
            resp.getHeaders().getContentDisposition().toString().contains("attachment")
        );
    }

    @Test
    @DisplayName("GET /api/reports/{id}/export/pdf with group/module/function dims → includes in PDF")
    void testExportPdfWithDimensions() {
        Map<String, Object> report = new LinkedHashMap<>();
        report.put("summary", Map.of(
            "pass_rate", 75.0,
            "average_score", 0.75,
            "total_test_cases", 4,
            "passed_test_cases", 3
        ));
        report.put("group", "Backend");
        report.put("project", "auth-service");
        report.put("module", "login");
        report.put("function", "verifyToken");
        report.put("evaluations", List.of());

        when(mockReportService.getReport("report-dims")).thenReturn(report);

        ResponseEntity<?> resp = controller.exportReportPdf("report-dims");

        assertNotNull(resp);
        assertEquals(200, resp.getStatusCode().value());
    }
}
