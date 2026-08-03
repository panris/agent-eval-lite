package io.github.panris.agenteval.service;

import io.github.panris.agenteval.model.ReportEntity;
import io.github.panris.agenteval.model.SharedReportEntity;
import io.github.panris.agenteval.repository.ReportJpaRepository;
import io.github.panris.agenteval.repository.SharedReportJpaRepository;
import org.junit.jupiter.api.*;

import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

/**
 * Unit tests for ReportService.
 * Uses pure Mockito mocks — no reflection, no file I/O.
 */
class ReportServiceTest {

    private ReportService reportService;
    // In-memory store for the mock JPA repo
    private final Map<String, ReportEntity> reportStore = new HashMap<>();
    private final Map<String, SharedReportEntity> sharedStore = new HashMap<>();

    @BeforeEach
    void setUp() {
        reportStore.clear();
        sharedStore.clear();

        ReportJpaRepository mockReportJpaRepo = mock(ReportJpaRepository.class);
        SharedReportJpaRepository mockSharedReportJpaRepo = mock(SharedReportJpaRepository.class);

        // findById: return stored entity if exists, otherwise empty (standard JPA behavior)
        when(mockReportJpaRepo.findById(anyString()))
            .thenAnswer(inv -> Optional.ofNullable(reportStore.get(inv.getArgument(0))));

        doAnswer(inv -> {
            // save() always writes the current state of the entity into the store
            ReportEntity entity = inv.getArgument(0);
            reportStore.put(entity.getId(), entity);
            return entity;
        }).when(mockReportJpaRepo).save(any(ReportEntity.class));
        // deleteById: remove from store (covers deleteReport)
        doAnswer(inv -> reportStore.remove(inv.getArgument(0))).when(mockReportJpaRepo).deleteById(anyString());
        // deleteAll: clear store (covers clearAllReports)
        doAnswer(inv -> { reportStore.clear(); return null; }).when(mockReportJpaRepo).deleteAll();

        when(mockReportJpaRepo.findAllOrderByTimestampDesc())
            .thenAnswer(inv -> {
                // Live snapshot — reads current store contents at call time
                List<ReportEntity> sorted = new ArrayList<>(reportStore.values());
                sorted.sort((a, b) -> Long.compare(
                    a.getTimestamp() != null ? a.getTimestamp() : 0L,
                    b.getTimestamp() != null ? b.getTimestamp() : 0L));
                return sorted;
            });
        // count: needed by cleanupOldReports
        when(mockReportJpaRepo.count()).thenAnswer(inv -> (long) reportStore.size());
        // existsById: needed by createShareLink
        when(mockReportJpaRepo.existsById(anyString())).thenAnswer(inv -> reportStore.containsKey(inv.getArgument(0)));
        // findByFavoriteTrue: needed by getFavorites (fallback path)
        when(mockReportJpaRepo.findByFavoriteTrue()).thenAnswer(inv -> reportStore.values().stream()
            .filter(e -> Boolean.TRUE.equals(e.getFavorite())).collect(java.util.stream.Collectors.toList()));
        // findFavoritesOrderByTimestampDesc: needed by getFavorites
        when(mockReportJpaRepo.findFavoritesOrderByTimestampDesc())
            .thenAnswer(inv -> reportStore.values().stream()
                .filter(e -> Boolean.TRUE.equals(e.getFavorite()))
                .sorted((a, b) -> Long.compare(
                    a.getTimestamp() != null ? a.getTimestamp() : 0L,
                    b.getTimestamp() != null ? b.getTimestamp() : 0L))
                .collect(java.util.stream.Collectors.toList()));

        // findByReportId: return shares for the given reportId (used by deleteReport cascade)
        when(mockSharedReportJpaRepo.findByReportId(anyString()))
            .thenAnswer(inv -> sharedStore.values().stream()
                .filter(e -> inv.getArgument(0).equals(e.getReportId()))
                .collect(java.util.stream.Collectors.toList()));
        when(mockSharedReportJpaRepo.findById(anyString()))
            .thenAnswer(inv -> Optional.ofNullable(sharedStore.get(inv.getArgument(0))));
        doAnswer(inv -> {
            SharedReportEntity e = inv.getArgument(0);
            sharedStore.put(e.getShareId(), e);
            return e;
        }).when(mockSharedReportJpaRepo).save(any(SharedReportEntity.class));
        doNothing().when(mockSharedReportJpaRepo).deleteAll();
        doAnswer(inv -> {
            ((List<SharedReportEntity>) inv.getArgument(0))
                .forEach(e -> sharedStore.remove(e.getShareId()));
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

    // ============ getReport ============

    @Test
    @DisplayName("getReport returns null for unknown ID")
    void testGetReportUnknown() {
        assertThat(reportService.getReport("unknown")).isNull();
    }
}
