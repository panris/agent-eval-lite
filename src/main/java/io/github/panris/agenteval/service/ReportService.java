package io.github.panris.agenteval.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 报告服务：管理评测报告的存储、查询、对比和分享。
 * 所有 Controller 应通过此服务操作报告，禁止直接操作 reportHistory。
 */
@Service
public class ReportService {
    private static final Logger log = LoggerFactory.getLogger(ReportService.class);
    private static final Path REPORTS_FILE = Paths.get("data/reports.json");

    private final Map<String, Map<String, Object>> reportHistory = new LinkedHashMap<>();
    private final Map<String, String> sharedReports = new LinkedHashMap<>(); // shareId -> reportId

    private final ObjectMapper objectMapper = new ObjectMapper();

    @PostConstruct
    public void init() {
        loadReportHistory();
        loadSharedReports();
    }

    // ============ 持久化 ============

    public void loadReportHistory() {
        if (!Files.exists(REPORTS_FILE)) return;
        try {
            String content = Files.readString(REPORTS_FILE);
            Map<String, Map<String, Object>> loaded = objectMapper.readValue(content,
                objectMapper.getTypeFactory().constructMapType(LinkedHashMap.class, String.class, Map.class));
            reportHistory.putAll(loaded);
            log.info("Loaded {} historical reports", reportHistory.size());
        } catch (Exception e) {
            log.error("Failed to load report history: {}", e.getMessage(), e);
        }
    }

    public void saveReportHistory() {
        try {
            Files.createDirectories(REPORTS_FILE.getParent());
            String json = objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(reportHistory);
            Files.writeString(REPORTS_FILE, json);
        } catch (Exception e) {
            log.error("Failed to save report history: {}", e.getMessage(), e);
        }
    }

    private void loadSharedReports() {
        Path sharesFile = Paths.get("data/shares.json");
        if (!Files.exists(sharesFile)) return;
        try {
            @SuppressWarnings("unchecked")
            Map<String, String> loaded = objectMapper.readValue(sharesFile.toFile(), Map.class);
            sharedReports.putAll(loaded);
            log.info("Loaded {} shared report links", sharedReports.size());
        } catch (Exception e) {
            log.error("Failed to load sharedReports: {}", e.getMessage(), e);
        }
    }

    public void saveSharedReports() {
        try {
            Path sharesFile = Paths.get("data/shares.json");
            Files.createDirectories(sharesFile.getParent());
            String json = objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(sharedReports);
            Files.writeString(sharesFile, json);
        } catch (Exception e) {
            log.error("Failed to save sharedReports: {}", e.getMessage(), e);
        }
    }

    // ============ 基础 CRUD ============

    public void saveReport(String reportId, Map<String, Object> report) {
        reportHistory.put(reportId, report);
        saveReportHistory();
    }

    public Map<String, Object> getReport(String reportId) {
        return reportHistory.get(reportId);
    }

    public List<Map<String, Object>> getAllReports(String sort, Long since, Long until) {
        List<Map<String, Object>> list = new ArrayList<>();
        for (Map.Entry<String, Map<String, Object>> e : reportHistory.entrySet()) {
            Map<String, Object> report = new LinkedHashMap<>(e.getValue());
            report.put("id", e.getKey()); // 让前端能拿到 reportId
            list.add(report);
        }

        // 按日期范围过滤
        if (since != null || until != null) {
            long sinceMs = since != null ? since : Long.MIN_VALUE;
            long untilMs = until != null ? until : Long.MAX_VALUE;
            list.removeIf(r -> {
                long ts = getTimestamp(r);
                return ts < sinceMs || ts > untilMs;
            });
        }

        if ("asc".equalsIgnoreCase(sort)) {
            list.sort(Comparator.comparing(r -> getTimestamp(r), Comparator.nullsLast(Comparator.naturalOrder())));
        } else {
            list.sort(Comparator.comparing(r -> getTimestamp(r), Comparator.nullsLast(Comparator.reverseOrder())));
        }
        return list;
    }

    public Map<String, Object> deleteReport(String reportId) {
        if (!reportHistory.containsKey(reportId)) {
            return Map.of("success", false, "error", "报告不存在");
        }
        reportHistory.remove(reportId);
        saveReportHistory();
        return Map.of("success", true, "message", "报告已删除");
    }

    public Map<String, Object> clearAllReports() {
        reportHistory.clear();
        saveReportHistory();
        return Map.of("success", true, "message", "所有报告已清除");
    }

    // ============ 报告操作 ============

    public Map<String, Object> copyReport(String reportId) {
        Map<String, Object> original = reportHistory.get(reportId);
        if (original == null) {
            return Map.of("success", false, "error", "报告不存在");
        }
        String newId = "report_" + System.currentTimeMillis();
        reportHistory.put(newId, new LinkedHashMap<>(original));
        saveReportHistory();
        return Map.of("success", true, "newId", newId, "message", "报告已复制");
    }

    public Map<String, Object> toggleFavorite(String reportId) {
        Map<String, Object> report = reportHistory.get(reportId);
        if (report == null) {
            return Map.of("success", false, "error", "报告不存在");
        }
        boolean current = (boolean) report.getOrDefault("favorite", false);
        report.put("favorite", !current);
        saveReportHistory();
        return Map.of("success", true, "favorite", !current);
    }

    public Map<String, Object> createShareLink(String reportId) {
        if (!reportHistory.containsKey(reportId)) {
            return Map.of("success", false, "error", "报告不存在");
        }
        String shareId = UUID.randomUUID().toString().substring(0, 8);
        sharedReports.put(shareId, reportId);
        saveSharedReports();
        return Map.of("success", true, "shareId", shareId, "url", "/share/" + shareId);
    }

    public String resolveShareId(String shareId) {
        return sharedReports.get(shareId);
    }

    public Map<String, Object> getFavorites() {
        Map<String, Map<String, Object>> favorites = new LinkedHashMap<>();
        reportHistory.forEach((reportId, report) -> {
            if ((boolean) report.getOrDefault("favorite", false)) {
                favorites.put(reportId, report);
            }
        });
        return Map.of("success", true, "favorites", favorites);
    }

    public Map<String, Object> updateTags(String reportId, List<String> tags) {
        Map<String, Object> report = reportHistory.get(reportId);
        if (report == null) {
            return Map.of("success", false, "error", "报告不存在");
        }
        report.put("tags", tags);
        saveReportHistory();
        return Map.of("success", true, "tags", tags);
    }

    public Map<String, Object> updateNote(String reportId, String note) {
        Map<String, Object> report = reportHistory.get(reportId);
        if (report == null) {
            return Map.of("success", false, "error", "报告不存在");
        }
        report.put("note", note != null ? note : "");
        saveReportHistory();
        return Map.of("success", true, "note", report.get("note"));
    }

    // ============ 报告对比 ============

    @SuppressWarnings("unchecked")
    public Map<String, Object> compareReports(List<String> reportIds) {
        List<Map<String, Object>> reports = reportIds.stream()
            .map(reportHistory::get)
            .filter(Objects::nonNull)
            .collect(Collectors.toList());

        if (reports.isEmpty()) {
            return Map.of("success", false, "error", "未找到有效报告");
        }

        Map<String, Object> comparison = new LinkedHashMap<>();
        comparison.put("count", reports.size());
        comparison.put("reports", reports);

        List<Double> scores = new ArrayList<>();
        List<Double> passRates = new ArrayList<>();
        List<Long> execTimes = new ArrayList<>();
        List<Integer> totalCases = new ArrayList<>();

        for (Map<String, Object> r : reports) {
            Object summaryObj = r.get("summary");
            if (summaryObj instanceof Map) {
                Map<?, ?> summary = (Map<?, ?>) summaryObj;

                Object scoreObj = summary.get("averageScore");
                if (scoreObj == null) scoreObj = summary.get("average_score");
                if (scoreObj instanceof Number) scores.add(((Number) scoreObj).doubleValue());

                Object prObj = summary.get("passRate");
                if (prObj == null) prObj = summary.get("pass_rate");
                if (prObj instanceof Number) passRates.add(((Number) prObj).doubleValue());

                Object tcObj = summary.get("totalTestCases");
                if (tcObj == null) tcObj = summary.get("total_test_cases");
                if (tcObj instanceof Number) totalCases.add(((Number) tcObj).intValue());
            }

            Object execObj = r.get("executionTimeMs");
            if (execObj instanceof Number) execTimes.add(((Number) execObj).longValue());
        }

        if (!scores.isEmpty()) {
            scores.sort(Double::compareTo);
            comparison.put("scoreStats", Map.of(
                "min", scores.get(0),
                "max", scores.get(scores.size() - 1),
                "avg", scores.stream().mapToDouble(Double::doubleValue).average().orElse(0)
            ));
        }

        if (!passRates.isEmpty()) {
            passRates.sort(Double::compareTo);
            comparison.put("passRateStats", Map.of(
                "min", passRates.get(0),
                "max", passRates.get(passRates.size() - 1),
                "avg", passRates.stream().mapToDouble(Double::doubleValue).average().orElse(0)
            ));
        }

        if (!execTimes.isEmpty()) {
            execTimes.sort(Long::compare);
            comparison.put("execTimeStats", Map.of(
                "min", execTimes.get(0),
                "max", execTimes.get(execTimes.size() - 1),
                "avg", execTimes.stream().mapToLong(Long::longValue).average().orElse(0)
            ));
        }

        if (!totalCases.isEmpty()) {
            comparison.put("totalCases", totalCases);
        }

        // 逐评分器明细：从 evaluations[].scorerResults 聚合各报告的评分器平均分
        Map<String, Map<String, List<Double>>> scorerScoresPerReport = new LinkedHashMap<>();
        for (int i = 0; i < reportIds.size(); i++) {
            String reportId = reportIds.get(i);
            Map<String, Object> r = reports.get(i); // 与 reportIds 一一对应
            Object evalsObj = r.get("evaluations");
            if (!(evalsObj instanceof List)) continue;
            for (Object evObj : (List<?>) evalsObj) {
                if (!(evObj instanceof Map)) continue;
                Map<?, ?> ev = (Map<?, ?>) evObj;
                Object srObj = ev.get("scorerResults");
                if (!(srObj instanceof Map)) continue;
                for (Map.Entry<?, ?> se : ((Map<?, ?>) srObj).entrySet()) {
                    String scorerName = String.valueOf(se.getKey());
                    if (!(se.getValue() instanceof Map)) continue;
                    Map<?, ?> sr = (Map<?, ?>) se.getValue();
                    Object scoreObj = sr.get("score");
                    if (!(scoreObj instanceof Number)) continue;
                    scorerScoresPerReport
                        .computeIfAbsent(scorerName, k -> new LinkedHashMap<>())
                        .computeIfAbsent(reportId, k -> new ArrayList<>())
                        .add(((Number) scoreObj).doubleValue());
                }
            }
        }

        // 转换为 { scorerName: { scores: { reportId: avgScore }, stats: {min/max/avg} } }
        if (!scorerScoresPerReport.isEmpty()) {
            Map<String, Map<String, Object>> scorerStats = new LinkedHashMap<>();
            for (Map.Entry<String, Map<String, List<Double>>> se : scorerScoresPerReport.entrySet()) {
                String scorer = se.getKey();
                Map<String, List<Double>> perReport = se.getValue();
                Map<String, Double> scoreMap = new LinkedHashMap<>();
                List<Double> all = new ArrayList<>();
                for (Map.Entry<String, List<Double>> pe : perReport.entrySet()) {
                    double avg = pe.getValue().stream().mapToDouble(Double::doubleValue).average().orElse(0);
                    scoreMap.put(pe.getKey(), Math.round(avg * 100.0) / 100.0);
                    all.addAll(pe.getValue());
                }
                all.sort(Double::compareTo);
                Map<String, Object> stats = Map.of(
                    "min", all.isEmpty() ? 0 : all.get(0),
                    "max", all.isEmpty() ? 0 : all.get(all.size() - 1),
                    "avg", all.isEmpty() ? 0 : Math.round(all.stream().mapToDouble(Double::doubleValue).average().orElse(0) * 100.0) / 100.0
                );
                scorerStats.put(scorer, Map.of("scores", scoreMap, "stats", stats));
            }
            comparison.put("scorerStats", scorerStats);
        }

        return Map.of("success", true, "comparison", comparison);
    }

    // ============ 工具方法 ============

    private Long getTimestamp(Map<String, Object> report) {
        Object ts = report.get("timestamp");
        if (ts instanceof Number) return ((Number) ts).longValue();
        return 0L;
    }

    /**
     * 清理过期报告，保留最新 maxReports 条。
     */
    public void cleanupOldReports(int maxReports) {
        if (reportHistory.size() <= maxReports) return;
        List<String> keys = getAllReports("desc", null, null).stream()
            .map(r -> reportHistory.entrySet().stream()
                .filter(e -> e.getValue() == r).findFirst().orElseThrow().getKey())
            .collect(Collectors.toList());

        int toRemove = reportHistory.size() - maxReports;
        for (int i = 0; i < toRemove; i++) {
            reportHistory.remove(keys.get(i));
        }
        saveReportHistory();
        log.info("Cleaned up {} old reports, kept {}", toRemove, maxReports);
    }
}
