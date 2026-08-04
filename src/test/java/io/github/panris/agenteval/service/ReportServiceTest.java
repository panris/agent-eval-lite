package io.github.panris.agenteval.service;

import io.github.panris.agenteval.model.ReportEntity;
import io.github.panris.agenteval.model.SharedReportEntity;
import io.github.panris.agenteval.repository.ReportJpaRepository;
import io.github.panris.agenteval.repository.SharedReportJpaRepository;
import org.junit.jupiter.api.*;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for ReportService.
 *
 * Uses in-memory store mocks (when().thenAnswer()) for the JPA repositories so the
 * report / share-link logic runs end-to-end without a database or disk persistence.
 * This avoids depending on internal fields (which were removed during the JPA migration).
 */
class ReportServiceTest {

    private ReportService reportService;
    private ReportJpaRepository mockReportJpaRepo;
    private SharedReportJpaRepository mockSharedReportJpaRepo;

    // in-memory stores backing the repository mocks
    private final Map<String, ReportEntity> reportStore = new ConcurrentHashMap<>();
    private final Map<String, SharedReportEntity> shareStore = new ConcurrentHashMap<>();

    @BeforeEach
    void setUp() {
        mockReportJpaRepo = mock(ReportJpaRepository.class);
        mockSharedReportJpaRepo = mock(SharedReportJpaRepository.class);

        // ---- ReportJpaRepository in-memory behavior ----
        when(mockReportJpaRepo.save(any(ReportEntity.class))).thenAnswer(inv -> {
            ReportEntity e = inv.getArgument(0);
            reportStore.put(e.getId(), e);
            return e;
        });
        when(mockReportJpaRepo.findById(anyString())).thenAnswer(inv ->
                Optional.ofNullable(reportStore.get(inv.getArgument(0))));
        when(mockReportJpaRepo.findAllOrderByTimestampDesc()).thenAnswer(inv ->
                reportStore.values().stream()
                        .sorted(Comparator.comparingLong((ReportEntity e) ->
                                e.getTimestamp() == null ? 0L : e.getTimestamp()).reversed())
                        .collect(Collectors.toList()));
        when(mockReportJpaRepo.findFavoritesOrderByTimestampDesc()).thenAnswer(inv ->
                reportStore.values().stream()
                        .filter(e -> e.getFavorite() != null && e.getFavorite())
                        .sorted(Comparator.comparingLong((ReportEntity e) ->
                                e.getTimestamp() == null ? 0L : e.getTimestamp()).reversed())
                        .collect(Collectors.toList()));
        when(mockReportJpaRepo.count()).thenAnswer(inv -> (long) reportStore.size());
        when(mockReportJpaRepo.existsById(anyString())).thenAnswer(inv ->
                reportStore.containsKey(inv.getArgument(0)));
        doAnswer(inv -> { reportStore.remove(inv.getArgument(0)); return null; })
                .when(mockReportJpaRepo).deleteById(anyString());
        doAnswer(inv -> { reportStore.clear(); return null; })
                .when(mockReportJpaRepo).deleteAll();

        // ---- SharedReportJpaRepository in-memory behavior ----
        when(mockSharedReportJpaRepo.save(any(SharedReportEntity.class))).thenAnswer(inv -> {
            SharedReportEntity e = inv.getArgument(0);
            shareStore.put(e.getShareId(), e);
            return e;
        });
        when(mockSharedReportJpaRepo.findById(anyString())).thenAnswer(inv ->
                Optional.ofNullable(shareStore.get(inv.getArgument(0))));
        when(mockSharedReportJpaRepo.findByReportId(anyString())).thenAnswer(inv ->
                shareStore.values().stream()
                        .filter(s -> inv.getArgument(0).equals(s.getReportId()))
                        .collect(Collectors.toList()));
        doAnswer(inv -> { shareStore.remove(inv.getArgument(0)); return null; })
                .when(mockSharedReportJpaRepo).deleteById(anyString());
        doAnswer(inv -> { shareStore.clear(); return null; })
                .when(mockSharedReportJpaRepo).deleteAll();
        doAnswer(inv -> {
                    for (Object o : (Iterable<?>) inv.getArgument(0)) {
                        shareStore.remove(((SharedReportEntity) o).getShareId());
                    }
                    return null;
                }).when(mockSharedReportJpaRepo).deleteAll(anyList());

        reportService = new ReportService(mockReportJpaRepo, mockSharedReportJpaRepo);
    }

    // ============ Delete / sharedReports cascade ============

    @Test
    @DisplayName("deleteReport removes report and its share link")
    void testDeleteReportCleansSharedLink() {
        Map<String, Object> report = new LinkedHashMap<>();
        report.put("summary", Map.of("pass_rate", 0.8));
        reportService.saveReport("r1", report);

        var shareResult = reportService.createShareLink("r1");
        String shareId = (String) shareResult.get("shareId");
        assertThat(shareId).isNotNull();
        assertThat(reportService.resolveShareId(shareId)).isEqualTo("r1");

        reportService.deleteReport("r1");

        assertThat(reportService.resolveShareId(shareId)).isNull();
        assertThat(reportService.getReport("r1")).isNull();
    }

    @Test
    @DisplayName("deleteReport returns error for non-existent report")
    void testDeleteNonExistentReport() {
        var result = reportService.deleteReport("nonexistent");
        assertThat(result.get("success")).isEqualTo(false);
        assertThat(result.get("error")).isEqualTo("报告不存在");
    }

    @Test
    @DisplayName("clearAllReports removes all reports and share links")
    void testClearAllReportsCleansEverything() {
        for (int i = 0; i < 3; i++) {
            Map<String, Object> r = new LinkedHashMap<>();
            r.put("summary", Map.of("pass_rate", 50.0));
            reportService.saveReport("r" + i, r);
            reportService.toggleFavorite("r" + i);
            reportService.createShareLink("r" + i);
        }
        assertThat((Integer) reportService.getFavorites().get("total")).isEqualTo(3);

        reportService.clearAllReports();

        var result = reportService.getAllReports("desc", null, null, null, null, null, null, null, null, null, null, 1, 20, false);

        @SuppressWarnings("unchecked")
        List<?> reports = (List<?>) result.get("reports");
        assertThat(reports).isEmpty();
        assertThat(reportService.getFavorites().get("total")).isEqualTo(0);
    }

    // ============ Share link management ============

    @Test
    @DisplayName("createShareLink generates 8-char shareId and returns URL")
    void testCreateShareLinkFormat() {
        Map<String, Object> report = new LinkedHashMap<>();
        report.put("summary", Map.of("pass_rate", 0.5));
        reportService.saveReport("r1", report);

        var result = reportService.createShareLink("r1");
        String shareId = (String) result.get("shareId");

        assertThat(shareId).hasSize(8);
        assertThat(result.get("url")).isEqualTo("/share/" + shareId);
    }

    @Test
    @DisplayName("resolveShareId returns null for unknown shareId")
    void testResolveUnknownShareId() {
        assertThat(reportService.resolveShareId("totallynew")).isNull();
    }

    @Test
    @DisplayName("createShareLink fails for non-existent report")
    void testShareNonExistentReport() {
        var result = reportService.createShareLink("nonexistent");
        assertThat(result.get("success")).isEqualTo(false);
    }

    // ============ Favorites ============

    @Test
    @DisplayName("toggleFavorite flips state correctly")
    void testToggleFavorite() {
        Map<String, Object> report = new LinkedHashMap<>();
        report.put("summary", Map.of("pass_rate", 0.5));
        reportService.saveReport("r1", report);

        assertThat(reportService.toggleFavorite("r1").get("favorite")).isEqualTo(true);
        assertThat(reportService.toggleFavorite("r1").get("favorite")).isEqualTo(false);
    }

    @Test
    @DisplayName("getFavorites returns only favorited reports")
    void testGetFavorites() {
        for (int i = 0; i < 4; i++) {
            Map<String, Object> r = new LinkedHashMap<>();
            r.put("summary", Map.of("pass_rate", 0.5));
            reportService.saveReport("r" + i, r);
        }
        reportService.toggleFavorite("r0");
        reportService.toggleFavorite("r2");

        assertThat((Integer) reportService.getFavorites().get("total")).isEqualTo(2);
    }

    // ============ Tags & Notes ============

    @Test
    @DisplayName("updateTags replaces tags on report")
    void testUpdateTags() {
        Map<String, Object> report = new LinkedHashMap<>();
        report.put("summary", Map.of("pass_rate", 0.5));
        reportService.saveReport("r1", report);

        var result = reportService.updateTags("r1", List.of("api", "v2"));
        @SuppressWarnings("unchecked")
        List<String> tags = (List<String>) result.get("tags");
        assertThat(tags).containsExactly("api", "v2");
    }

    @Test
    @DisplayName("updateNote sets note on report")
    void testUpdateNote() {
        Map<String, Object> report = new LinkedHashMap<>();
        report.put("summary", Map.of("pass_rate", 0.5));
        reportService.saveReport("r1", report);

        var result = reportService.updateNote("r1", "important regression test");
        assertThat(result.get("note")).isEqualTo("important regression test");
    }

    // ============ getAllReports filtering ============

    @Test
    @DisplayName("Filters by group (case-insensitive)")
    void testFilterByGroup() {
        for (String g : List.of("GroupA", "GroupB")) {
            Map<String, Object> r = new LinkedHashMap<>();
            r.put("group", g);
            r.put("summary", Map.of("pass_rate", 0.5));
            reportService.saveReport(g + "1", r);
        }

        var result = reportService.getAllReports("desc", null, null, "GroupA", null, null, null, null, null, null, null, 1, 20, false);

        @SuppressWarnings("unchecked")
        List<?> reports = (List<?>) result.get("reports");
        assertThat(reports).hasSize(1);
    }

    @Test
    @DisplayName("Filters by favorite=true")
    void testFilterByFavorite() {
        Map<String, Object> r1 = new LinkedHashMap<>();
        r1.put("summary", Map.of("pass_rate", 0.5));
        reportService.saveReport("r1", r1);
        reportService.toggleFavorite("r1");

        Map<String, Object> r2 = new LinkedHashMap<>();
        r2.put("summary", Map.of("pass_rate", 0.5));
        reportService.saveReport("r2", r2);

        var result = reportService.getAllReports("desc", null, null, null, null, null, null, true, null, null, null, 1, 20, false);

        @SuppressWarnings("unchecked")
        List<?> reports = (List<?>) result.get("reports");
        assertThat(reports).hasSize(1);
        assertThat(((Map<?, ?>) reports.get(0)).get("id")).isEqualTo("r1");
    }

    @Test
    @DisplayName("Filters by status=passed (pass_rate >= 0.7)")
    void testFilterByStatusPassed() {
        Map<String, Object> p = new LinkedHashMap<>();
        p.put("summary", Map.of("pass_rate", 85.0));
        reportService.saveReport("passed", p);

        Map<String, Object> f = new LinkedHashMap<>();
        f.put("summary", Map.of("pass_rate", 30.0));
        reportService.saveReport("failed", f);

        var result = reportService.getAllReports("desc", null, null, null, null, null, null, null, "passed", null, null, 1, 20, false);

        @SuppressWarnings("unchecked")
        List<?> reports = (List<?>) result.get("reports");
        assertThat(reports).hasSize(1);
    }

    @Test
    @DisplayName("Filters by status=failed (pass_rate < 0.7)")
    void testFilterByStatusFailed() {
        Map<String, Object> p = new LinkedHashMap<>();
        p.put("summary", Map.of("pass_rate", 85.0));
        reportService.saveReport("passed", p);

        Map<String, Object> f = new LinkedHashMap<>();
        f.put("summary", Map.of("pass_rate", 30.0));
        reportService.saveReport("failed", f);

        var result = reportService.getAllReports("desc", null, null, null, null, null, null, null, "failed", null, null, 1, 20, false);

        @SuppressWarnings("unchecked")
        List<?> reports = (List<?>) result.get("reports");
        assertThat(reports).hasSize(1);
    }

    @Test
    @DisplayName("Filters by keyword (case-insensitive, matches id/note/tags)")
    void testFilterByKeyword() {
        Map<String, Object> r1 = new LinkedHashMap<>();
        r1.put("note", "API regression test");
        r1.put("tags", List.of("critical"));
        r1.put("summary", Map.of("pass_rate", 0.5));
        reportService.saveReport("r1", r1);

        Map<String, Object> r2 = new LinkedHashMap<>();
        r2.put("note", "UI smoke test");
        r2.put("tags", List.of("minor"));
        r2.put("summary", Map.of("pass_rate", 0.5));
        reportService.saveReport("r2", r2);

        var result = reportService.getAllReports("desc", null, null, null, null, null, null, null, null, "api", null, 1, 20, false);

        @SuppressWarnings("unchecked")
        List<?> reports = (List<?>) result.get("reports");
        assertThat(reports).hasSize(1);
        assertThat(((Map<?, ?>) reports.get(0)).get("id")).isEqualTo("r1");
    }

    @Test
    @DisplayName("all=true ignores pagination and returns full list")
    void testAllParamReturnsFullList() {
        for (int i = 0; i < 8; i++) {
            Map<String, Object> r = new LinkedHashMap<>();
            r.put("summary", Map.of("pass_rate", 0.5));
            reportService.saveReport("r" + i, r);
        }

        var result = reportService.getAllReports("desc", null, null, null, null, null, null, null, null, null, "time", 1, 3, true);

        @SuppressWarnings("unchecked")
        List<?> reports = (List<?>) result.get("reports");
        assertThat(reports).hasSize(8); // size=3 ignored when all=true
    }

    @Test
    @DisplayName("Respects sort order asc/desc")
    void testSortOrder() {
        for (int i = 0; i < 3; i++) {
            Map<String, Object> r = new LinkedHashMap<>();
            r.put("timestamp", 1000L + i);
            r.put("summary", Map.of("pass_rate", 0.5));
            reportService.saveReport("r" + i, r);
        }

        var asc = reportService.getAllReports("asc", null, null, null, null, null, null, null, null, null, "time", 1, 20, false);

        @SuppressWarnings("unchecked")
        List<?> ascReports = (List<?>) asc.get("reports");
        assertThat(((Map<?, ?>) ascReports.get(0)).get("id")).isEqualTo("r0");
        assertThat(((Map<?, ?>) ascReports.get(2)).get("id")).isEqualTo("r2");
    }

    // ============ cleanupOldReports ============

    @Test
    @DisplayName("cleanupOldReports removes oldest reports and their share links")
    void testCleanupOldReportsCleansShares() {
        for (int i = 0; i < 5; i++) {
            Map<String, Object> r = new LinkedHashMap<>();
            r.put("timestamp", 1000L + i);
            r.put("summary", Map.of("pass_rate", 0.5));
            reportService.saveReport("r" + i, r);
            reportService.createShareLink("r" + i);
        }

        reportService.cleanupOldReports(3);

        var result = reportService.getAllReports("desc", null, null, null, null, null, null, null, null, null, null, 1, 20, false);

        @SuppressWarnings("unchecked")
        List<?> reports = (List<?>) result.get("reports");
        assertThat(reports).hasSize(3);
        // Old share links should be cleaned up
        assertThat(reportService.getReport("r0")).isNull();
        assertThat(reportService.getReport("r1")).isNull();
    }

    // ============ copyReport ============

    @Test
    @DisplayName("copyReport creates new report with different ID")
    void testCopyReport() {
        Map<String, Object> r = new LinkedHashMap<>();
        r.put("note", "original note");
        r.put("summary", Map.of("pass_rate", 70.0));
        reportService.saveReport("r1", r);

        var result = reportService.copyReport("r1");
        String newId = (String) result.get("newId");
        assertThat(newId).isNotEqualTo("r1");
        assertThat(reportService.getReport(newId)).isNotNull();
        assertThat(((Map<?, ?>) reportService.getReport(newId)).get("note")).isEqualTo("original note");
    }

    // ============ getDashboardStats ============

    @Test
    @DisplayName("getDashboardStats 汇总报告总数/平均通过率/平均响应时间")
    void testGetDashboardStats() {
        reportStore.clear();
        reportStore.put("r1", buildReportEntity("r1", 80.0, 1000L));
        reportStore.put("r2", buildReportEntity("r2", 60.0, 3000L));

        Map<String, Object> stats = reportService.getDashboardStats();

        assertThat((Long) stats.get("totalReports")).isEqualTo(2L);
        assertThat((Double) stats.get("avgPassRate")).isEqualTo(70.0);
        assertThat((Double) stats.get("avgResponseTime")).isEqualTo(2000.0);
    }

    @Test
    @DisplayName("getDashboardStats 无报告时返回零值")
    void testGetDashboardStatsEmpty() {
        reportStore.clear();
        Map<String, Object> stats = reportService.getDashboardStats();
        assertThat((Long) stats.get("totalReports")).isEqualTo(0L);
        assertThat((Double) stats.get("avgPassRate")).isEqualTo(0.0);
        assertThat((Double) stats.get("avgResponseTime")).isEqualTo(0.0);
    }

    private ReportEntity buildReportEntity(String id, double passRate, long execMs) {
        ReportEntity e = new ReportEntity(id);
        e.setSummaryJson("{\"pass_rate\":" + passRate + "}");
        e.setExecutionTimeMs(execMs);
        e.setTimestamp(System.currentTimeMillis());
        return e;
    }
}
