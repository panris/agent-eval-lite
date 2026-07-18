package io.github.panris.agenteval.service;


import io.github.panris.agenteval.web.dto.ApiResponse;
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
    private static String dataDir() {
        return System.getProperty("agenteval.data.dir", "data");
    }
    private static Path reportsFile() {
        return Paths.get(dataDir(), "reports.json");
    }

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
        if (!Files.exists(reportsFile())) return;
        try {
            String content = Files.readString(reportsFile());
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
            Files.createDirectories(reportsFile().getParent());
            String json = objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(reportHistory);
            Files.writeString(reportsFile(), json);
        } catch (Exception e) {
            log.error("Failed to save report history: {}", e.getMessage(), e);
        }
    }

    private void loadSharedReports() {
        Path sharesFile = Paths.get(dataDir(), "shares.json");
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
            Path sharesFile = Paths.get(dataDir(), "shares.json");
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

    public Map<String, Object> getAllReports(String sort, Long since, Long until, String group, String project,
                                              String module, String function, Boolean favorite, String status,
                                              String keyword, String sortBy, int page, int size, boolean all) {
        List<Map<String, Object>> list = new ArrayList<>();
        for (Map.Entry<String, Map<String, Object>> e : reportHistory.entrySet()) {
            Map<String, Object> report = new LinkedHashMap<>(e.getValue());
            report.put("id", e.getKey());
            list.add(report);
        }

        // 按分组过滤
        if (group != null && !group.trim().isEmpty()) {
            String g = group.trim();
            list.removeIf(r -> !g.equalsIgnoreCase(String.valueOf(r.getOrDefault("group", ""))));
        }

        // 按三维分组过滤
        if (project != null && !project.trim().isEmpty()) {
            String p = project.trim();
            list.removeIf(r -> !p.equalsIgnoreCase(String.valueOf(r.getOrDefault("project", ""))));
        }
        if (module != null && !module.trim().isEmpty()) {
            String m = module.trim();
            list.removeIf(r -> !m.equalsIgnoreCase(String.valueOf(r.getOrDefault("module", ""))));
        }
        if (function != null && !function.trim().isEmpty()) {
            String f = function.trim();
            list.removeIf(r -> !f.equalsIgnoreCase(String.valueOf(r.getOrDefault("function", ""))));
        }

        // 按收藏过滤
        if (favorite != null) {
            final boolean fv = favorite;
            list.removeIf(r -> Boolean.TRUE.equals(r.get("favorite")) != fv);
        }
        // 按状态过滤（通过率 >= 70 视为通过）
        if (status != null && !status.trim().isEmpty()) {
            final String st = status.trim().toLowerCase();
            list.removeIf(r -> {
                double pr = extractPassRate(r.get("summary"));
                boolean passed = pr >= 70.0;
                if ("passed".equals(st)) return !passed;
                if ("failed".equals(st)) return passed;
                return false;
            });
        }
        // 按关键字过滤（id / 备注 / 标签）
        if (keyword != null && !keyword.trim().isEmpty()) {
            final String kw = keyword.trim().toLowerCase();
            list.removeIf(r -> {
                String id = String.valueOf(r.getOrDefault("id", "")).toLowerCase();
                String note = String.valueOf(r.getOrDefault("note", "")).toLowerCase();
                StringBuilder tagsB = new StringBuilder();
                Object tagsObj = r.get("tags");
                if (tagsObj instanceof java.util.List) {
                    for (Object t : (java.util.List<?>) tagsObj) {
                        if (tagsB.length() > 0) tagsB.append(' ');
                        tagsB.append(t);
                    }
                }
                String tags = tagsB.toString().toLowerCase();
                return !(id.contains(kw) || note.contains(kw) || tags.contains(kw));
            });
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

        boolean asc = "asc".equalsIgnoreCase(sort);
        list.sort((a, b) -> {
            if ("score".equalsIgnoreCase(sortBy)) {
                Double sa = extractScore(a.get("summary")), sb = extractScore(b.get("summary"));
                if (sa != null && sb != null) {
                    int cmp = Double.compare(sa, sb);
                    if (cmp != 0) return asc ? cmp : -cmp;
                }
            }
            long tsA = getTimestamp(a), tsB = getTimestamp(b);
            return asc ? Long.compare(tsA, tsB) : Long.compare(tsB, tsA);
        });

        // 全量返回（趋势图用，复用相同过滤条件，不分页）
        if (all) {
            Map<String, Object> allResult = new LinkedHashMap<>();
            allResult.put("reports", list);
            allResult.put("total", reportHistory.size());
            allResult.put("filtered", list.size());
            allResult.put("page", 1);
            allResult.put("size", list.size());
            allResult.put("totalPages", 1);
            return allResult;
        }

        // 分页
        int total = reportHistory.size();
        int filtered = list.size();
        int totalPages = Math.max(1, (int) Math.ceil((double) filtered / size));
        if (page < 1) page = 1;
        if (page > totalPages) page = totalPages;
        int from = (page - 1) * size;
        int to = Math.min(from + size, filtered);
        List<Map<String, Object>> paged = from < filtered ? list.subList(from, to) : List.of();

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("reports", paged);
        result.put("total", total);
        result.put("filtered", filtered);
        result.put("page", page);
        result.put("size", size);
        result.put("totalPages", totalPages);
        return result;
    }

    private Double extractScore(Object summaryObj) {
        if (!(summaryObj instanceof Map)) return null;
        Map<?, ?> s = (Map<?, ?>) summaryObj;
        Object score = s.get("averageScore");
        if (score == null) score = s.get("average_score");
        if (score instanceof Number) return ((Number) score).doubleValue();
        return null;
    }

    private double extractPassRate(Object summaryObj) {
        if (!(summaryObj instanceof Map)) return 0.0;
        Map<?, ?> s = (Map<?, ?>) summaryObj;
        Object pr = s.get("pass_rate");
        if (pr == null) pr = s.get("passRate");
        if (pr instanceof Number) return ((Number) pr).doubleValue();
        return 0.0;
    }

    public Map<String, Object> deleteReport(String reportId) {
        if (!reportHistory.containsKey(reportId)) {
            return ApiResponse.error("报告不存在");
        }
        reportHistory.remove(reportId);
        removeShareByReportId(reportId);
        saveReportHistory();
        saveSharedReports();
        return ApiResponse.success("message", "报告已删除");
    }

    public Map<String, Object> clearAllReports() {
        reportHistory.clear();
        sharedReports.clear();
        saveReportHistory();
        saveSharedReports();
        return ApiResponse.success("message", "所有报告已清除");
    }

    /**
     * 清理指定 reportId 对应的所有分享链接。
     */
    private void removeShareByReportId(String reportId) {
        sharedReports.entrySet().removeIf(e -> reportId.equals(e.getValue()));
    }

    // ============ 报告操作 ============

    public Map<String, Object> copyReport(String reportId) {
        Map<String, Object> original = reportHistory.get(reportId);
        if (original == null) {
            return ApiResponse.error("报告不存在");
        }
        String newId = "report_" + System.currentTimeMillis();
        reportHistory.put(newId, new LinkedHashMap<>(original));
        saveReportHistory();
        return ApiResponse.success(Map.of("newId", newId, "message", "报告已复制"));
    }

    public Map<String, Object> toggleFavorite(String reportId) {
        Map<String, Object> report = reportHistory.get(reportId);
        if (report == null) {
            return ApiResponse.error("报告不存在");
        }
        boolean current = (boolean) report.getOrDefault("favorite", false);
        report.put("favorite", !current);
        saveReportHistory();
        return ApiResponse.success("favorite", !current);
    }

    public Map<String, Object> createShareLink(String reportId) {
        if (!reportHistory.containsKey(reportId)) {
            return ApiResponse.error("报告不存在");
        }
        String shareId = UUID.randomUUID().toString().substring(0, 8);
        sharedReports.put(shareId, reportId);
        saveSharedReports();
        return ApiResponse.success(Map.of("shareId", shareId, "url", "/share/" + shareId));
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
        return ApiResponse.success(Map.of("favorites", favorites, "total", favorites.size()));
    }

    public Map<String, Object> updateTags(String reportId, List<String> tags) {
        Map<String, Object> report = reportHistory.get(reportId);
        if (report == null) {
            return ApiResponse.error("报告不存在");
        }
        report.put("tags", tags);
        saveReportHistory();
        return ApiResponse.success("tags", tags);
    }

    public Map<String, Object> updateNote(String reportId, String note) {
        Map<String, Object> report = reportHistory.get(reportId);
        if (report == null) {
            return ApiResponse.error("报告不存在");
        }
        report.put("note", note != null ? note : "");
        saveReportHistory();
        return ApiResponse.success("note", report.get("note"));
    }

    // ============ 报告对比 ============

    @SuppressWarnings("unchecked")
    public Map<String, Object> compareReports(List<String> reportIds) {
        List<Map<String, Object>> reports = reportIds.stream()
            .map(reportHistory::get)
            .filter(Objects::nonNull)
            .collect(Collectors.toList());

        if (reports.isEmpty()) {
            return ApiResponse.error("未找到有效报告");
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

        return ApiResponse.success("comparison", comparison);
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
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> sorted = (List<Map<String, Object>>) getAllReports("desc", null, null, null, null, null, null, null, null, null, "time", 1, 10000, false).get("reports");
        int toRemove = reportHistory.size() - maxReports;
        // sorted is descending by timestamp (newest first), so iterate backward to remove the oldest
        for (int i = sorted.size() - 1; i >= sorted.size() - toRemove; i--) {
            String id = sorted.get(i).get("id").toString();
            reportHistory.remove(id);
            removeShareByReportId(id);
        }
        saveReportHistory();
        saveSharedReports();
        log.info("自动清理 {} 条旧报告，保留最近 {} 条", toRemove, maxReports);
    }
}
