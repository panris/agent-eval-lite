package io.github.panris.agenteval.service;

import org.junit.jupiter.api.*;
import org.springframework.ui.Model;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Pure unit tests for ReportCleanupService — no Spring context needed.
 */
class ReportCleanupServiceTest {

    private ReportService mockReportService;

    @BeforeEach
    void setUp() {
        mockReportService = mock(ReportService.class);
    }

    // ============ @PostConstruct onStartup ============

    @Test
    @DisplayName("onStartup calls cleanupOldReports with MAX_REPORTS=500")
    void testOnStartupCallsCleanup() {
        ReportCleanupService service = new ReportCleanupService(mockReportService);

        service.onStartup();

        verify(mockReportService).cleanupOldReports(500);
    }

    @Test
    @DisplayName("onStartup swallows exceptions gracefully")
    void testOnStartupSwallowsException() {
        doThrow(new RuntimeException("disk error")).when(mockReportService).cleanupOldReports(500);
        ReportCleanupService service = new ReportCleanupService(mockReportService);

        // Should not throw
        assertDoesNotThrow(() -> service.onStartup());
    }

    // ============ @Scheduled scheduledCleanup ============

    @Test
    @DisplayName("scheduledCleanup calls cleanupOldReports with MAX_REPORTS=500")
    void testScheduledCleanupCallsCleanup() {
        ReportCleanupService service = new ReportCleanupService(mockReportService);

        service.scheduledCleanup();

        verify(mockReportService).cleanupOldReports(500);
    }

    @Test
    @DisplayName("scheduledCleanup logs and swallows exceptions gracefully")
    void testScheduledCleanupSwallowsException() {
        doThrow(new RuntimeException("cleanup error")).when(mockReportService).cleanupOldReports(500);
        ReportCleanupService service = new ReportCleanupService(mockReportService);

        // Should not throw
        assertDoesNotThrow(() -> service.scheduledCleanup());
    }

    @Test
    @DisplayName("scheduledCleanup can be called multiple times")
    void testScheduledCleanupMultipleCalls() {
        ReportCleanupService service = new ReportCleanupService(mockReportService);

        service.scheduledCleanup();
        service.scheduledCleanup();
        service.scheduledCleanup();

        verify(mockReportService, times(3)).cleanupOldReports(500);
    }
}
