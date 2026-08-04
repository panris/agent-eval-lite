package io.github.panris.agenteval.service;

import io.github.panris.agenteval.model.ReportEntity;
import io.github.panris.agenteval.repository.ReportJpaRepository;
import io.github.panris.agenteval.repository.SharedReportJpaRepository;
import io.github.panris.agenteval.web.dto.ApiResponse;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

@Service
public class ReportService {
    private static final Logger log = LoggerFactory.getLogger(ReportService.class);

    private final ReportJpaRepository reportJpaRepository;
    private final SharedReportJpaRepository sharedReportJpaRepository;
    private final ObjectMapper objectMapper;

    public ReportService(ReportJpaRepository reportJpaRepository,
                         SharedReportJpaRepository sharedReportJpaRepository) {
        this.reportJpaRepository = reportJpaRepository;
        this.sharedReportJpaRepository = sharedReportJpaRepository;
        this.objectMapper = new ObjectMapper();
    }

    public void saveReport(String reportId, Map<String, Object> report) {
        ReportEntity entity = reportJpaRepository.findById(reportId)
            .orElse(new ReportEntity(reportId));

        Object summary = report.get("summary");
        if (summary != null) {
            try {
                entity.setSummaryJson(objectMapper.writeValueAsString(summary));
            } catch (Exception e) {
                log.warn("Failed to serialize summary", e);
            }
        }

        Object evaluations = report.get("evaluations");
        if (evaluations != null) {
            try {
                entity.setEvaluationsJson(objectMapper.writeValueAsString(evaluations));
            } catch (Exception e) {
                log.warn("Failed to serialize evaluations", e);
            }
        }

        entity.setTotalTestCases(getIntValue(report, "totalTestCases", "total_test_cases"));
        entity.setPassedTestCases(getIntValue(report, "passedTestCases", "passed_test_cases"));
        entity.setFailedTestCases(getIntValue(report, "failedTestCases", "failed_test_cases"));
        entity.setExecutionTimeMs(getLongValue(report, "executionTimeMs", "execution_time_ms"));
        Long ts = getLongValue(report, "timestamp");
        entity.setTimestamp(ts != null ? ts : System.currentTimeMillis());
        entity.setFavorite((Boolean) report.getOrDefault("favorite", false));
        entity.setNote((String) report.get("note"));
        entity.setGroup((String) report.get("group"));
        entity.setProject((String) report.get("project"));
        entity.setModule((String) report.get("module"));
        entity.setFunction((String) report.get("function"));
        entity.setAsyncTaskId((String) report.get("asyncTaskId"));

        Object tags = report.get("tags");
        if (tags != null) {
            try {
                entity.setTagsJson(objectMapper.writeValueAsString(tags));
            } catch (Exception e) {
                log.warn("Failed to serialize tags", e);
            }
        }

        reportJpaRepository.save(entity);
        log.info("Saved report: {}", reportId);
    }

    public Map<String, Object> getReport(String reportId) {
        return reportJpaRepository.findById(reportId)
            .map(this::entityToMap)
            .orElse(null);
    }

    public Map<String, Object> getAllReports(String sort, Long since, Long until, String group, String project,
                                              String module, String function, Boolean favorite, String status,
                                              String keyword, String sortBy, int page, int size, boolean all) {
        List<ReportEntity> allEntities = reportJpaRepository.findAllOrderByTimestampDesc();
        List<Map<String, Object>> list = allEntities.stream()
            .map(this::entityToMap)
            .collect(Collectors.toList());

        list.removeIf(r -> {
            if (group != null && !group.trim().isEmpty()) {
                String g = group.trim();
                if (!g.equalsIgnoreCase(String.valueOf(r.getOrDefault("group", "")))) return true;
            }

            if (project != null && !project.trim().isEmpty()) {
                String p = project.trim();
                if (!p.equalsIgnoreCase(String.valueOf(r.getOrDefault("project", "")))) return true;
            }
            if (module != null && !module.trim().isEmpty()) {
                String m = module.trim();
                if (!m.equalsIgnoreCase(String.valueOf(r.getOrDefault("module", "")))) return true;
            }
            if (function != null && !function.trim().isEmpty()) {
                String f = function.trim();
                if (!f.equalsIgnoreCase(String.valueOf(r.getOrDefault("function", "")))) return true;
            }

            if (favorite != null) {
                final boolean fv = favorite;
                if (Boolean.TRUE.equals(r.get("favorite")) != fv) return true;
            }

            if (status != null && !status.trim().isEmpty()) {
                final String st = status.trim().toLowerCase();
                double pr = extractPassRate(r.get("summary"));
                boolean passed = pr >= 70.0;
                if ("passed".equals(st) && !passed) return true;
                if ("failed".equals(st) && passed) return true;
            }

            if (keyword != null && !keyword.trim().isEmpty()) {
                final String kw = keyword.trim().toLowerCase();
                String id = String.valueOf(r.getOrDefault("id", "")).toLowerCase();
                String note = String.valueOf(r.getOrDefault("note", "")).toLowerCase();
                StringBuilder tagsB = new StringBuilder();
                Object tagsObj = r.get("tags");
                if (tagsObj instanceof List) {
                    for (Object t : (List<?>) tagsObj) {
                        if (tagsB.length() > 0) tagsB.append(' ');
                        tagsB.append(t);
                    }
                }
                String tags = tagsB.toString().toLowerCase();
                if (!(id.contains(kw) || note.contains(kw) || tags.contains(kw))) return true;
            }

            if (since != null || until != null) {
                long sinceMs = since != null ? since : Long.MIN_VALUE;
                long untilMs = until != null ? until : Long.MAX_VALUE;
                long ts = getTimestamp(r);
                if (ts < sinceMs || ts > untilMs) return true;
            }

            return false;
        });

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

        if (all) {
            Map<String, Object> allResult = new LinkedHashMap<>();
            allResult.put("reports", list);
            allResult.put("total", reportJpaRepository.count());
            allResult.put("filtered", list.size());
            allResult.put("page", 1);
            allResult.put("size", list.size());
            allResult.put("totalPages", 1);
            return allResult;
        }

        int total = (int) reportJpaRepository.count();
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

    /**
     * 首页控制台统计：报告总数、平均通过率、平均响应时间。
     * 服务端预填充，避免首屏展示占位符 "-"（前端 JS 仍会在加载后刷新）。
     */
    public Map<String, Object> getDashboardStats() {
        Map<String, Object> all = getAllReports("desc", null, null, null, null, null, null,
                null, null, null, "timestamp", 1, Integer.MAX_VALUE, true);
        List<Map<String, Object>> reports =
                (List<Map<String, Object>>) all.getOrDefault("reports", List.of());
        long totalReports = ((Number) all.getOrDefault("total", reports.size())).longValue();

        double sumPass = 0.0;
        int passCount = 0;
        double sumExec = 0.0;
        int execCount = 0;
        for (Map<String, Object> r : reports) {
            Object summary = r.get("summary");
            if (summary instanceof Map) {
                sumPass += extractPassRate(summary);
                passCount++;
            }
            Object exec = r.get("executionTimeMs");
            if (exec instanceof Number) {
                sumExec += ((Number) exec).doubleValue();
                execCount++;
            }
        }
        double avgPassRate = passCount > 0 ? sumPass / passCount : 0.0;
        double avgResponseTime = execCount > 0 ? sumExec / execCount : 0.0;

        Map<String, Object> stats = new LinkedHashMap<>();
        stats.put("totalReports", totalReports);
        stats.put("avgPassRate", Math.round(avgPassRate * 10.0) / 10.0);
        stats.put("avgResponseTime", (double) Math.round(avgResponseTime));
        return stats;
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
        if (!reportJpaRepository.existsById(reportId)) {
            return ApiResponse.error("报告不存在");
        }
        reportJpaRepository.deleteById(reportId);
        removeShareByReportId(reportId);
        return ApiResponse.success("message", "报告已删除");
    }

    public Map<String, Object> clearAllReports() {
        reportJpaRepository.deleteAll();
        sharedReportJpaRepository.deleteAll();
        return ApiResponse.success("message", "所有报告已清除");
    }

    private void removeShareByReportId(String reportId) {
        List<io.github.panris.agenteval.model.SharedReportEntity> shares = sharedReportJpaRepository.findByReportId(reportId);
        sharedReportJpaRepository.deleteAll(shares);
    }

    public Map<String, Object> copyReport(String reportId) {
        Optional<ReportEntity> opt = reportJpaRepository.findById(reportId);
        if (opt.isEmpty()) {
            return ApiResponse.error("报告不存在");
        }
        ReportEntity original = opt.get();
        String newId = "report_" + System.currentTimeMillis();
        ReportEntity copy = new ReportEntity(newId);
        
        copy.setSummaryJson(original.getSummaryJson());
        copy.setEvaluationsJson(original.getEvaluationsJson());
        copy.setTotalTestCases(original.getTotalTestCases());
        copy.setPassedTestCases(original.getPassedTestCases());
        copy.setFailedTestCases(original.getFailedTestCases());
        copy.setExecutionTimeMs(original.getExecutionTimeMs());
        copy.setTimestamp(System.currentTimeMillis());
        copy.setFavorite(false);
        copy.setTagsJson(original.getTagsJson());
        copy.setNote(original.getNote());
        copy.setGroup(original.getGroup());
        copy.setProject(original.getProject());
        copy.setModule(original.getModule());
        copy.setFunction(original.getFunction());

        reportJpaRepository.save(copy);
        return ApiResponse.success(Map.of("newId", newId, "message", "报告已复制"));
    }

    public Map<String, Object> toggleFavorite(String reportId) {
        Optional<ReportEntity> opt = reportJpaRepository.findById(reportId);
        if (opt.isEmpty()) {
            return ApiResponse.error("报告不存在");
        }
        ReportEntity entity = opt.get();
        boolean current = entity.getFavorite() != null && entity.getFavorite();
        entity.setFavorite(!current);
        reportJpaRepository.save(entity);
        return ApiResponse.success("favorite", !current);
    }

    public Map<String, Object> createShareLink(String reportId) {
        if (!reportJpaRepository.existsById(reportId)) {
            return ApiResponse.error("报告不存在");
        }
        String shareId = UUID.randomUUID().toString().substring(0, 8);
        sharedReportJpaRepository.save(new io.github.panris.agenteval.model.SharedReportEntity(shareId, reportId));
        return ApiResponse.success(Map.of("shareId", shareId, "url", "/share/" + shareId));
    }

    public String resolveShareId(String shareId) {
        return sharedReportJpaRepository.findById(shareId)
            .map(io.github.panris.agenteval.model.SharedReportEntity::getReportId)
            .orElse(null);
    }

    public Map<String, Object> getFavorites() {
        List<ReportEntity> favorites = reportJpaRepository.findFavoritesOrderByTimestampDesc();
        Map<String, Map<String, Object>> favoriteMap = new LinkedHashMap<>();
        for (ReportEntity entity : favorites) {
            favoriteMap.put(entity.getId(), entityToMap(entity));
        }
        return ApiResponse.success(Map.of("favorites", favoriteMap, "total", favoriteMap.size()));
    }

    public Map<String, Object> updateTags(String reportId, List<String> tags) {
        Optional<ReportEntity> opt = reportJpaRepository.findById(reportId);
        if (opt.isEmpty()) {
            return ApiResponse.error("报告不存在");
        }
        ReportEntity entity = opt.get();
        try {
            entity.setTagsJson(objectMapper.writeValueAsString(tags));
        } catch (Exception e) {
            log.warn("Failed to serialize tags", e);
        }
        reportJpaRepository.save(entity);
        return ApiResponse.success("tags", tags);
    }

    public Map<String, Object> updateNote(String reportId, String note) {
        Optional<ReportEntity> opt = reportJpaRepository.findById(reportId);
        if (opt.isEmpty()) {
            return ApiResponse.error("报告不存在");
        }
        ReportEntity entity = opt.get();
        entity.setNote(note != null ? note : "");
        reportJpaRepository.save(entity);
        return ApiResponse.success("note", entity.getNote());
    }

    @SuppressWarnings("unchecked")
    public Map<String, Object> compareReports(List<String> reportIds) {
        List<ReportEntity> entities = reportIds.stream()
            .map(reportJpaRepository::findById)
            .filter(Optional::isPresent)
            .map(Optional::get)
            .collect(Collectors.toList());

        if (entities.isEmpty()) {
            return ApiResponse.error("未找到有效报告");
        }

        List<Map<String, Object>> reports = entities.stream()
            .map(this::entityToMap)
            .collect(Collectors.toList());

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

        Map<String, Map<String, List<Double>>> scorerScoresPerReport = new LinkedHashMap<>();
        for (int i = 0; i < reportIds.size(); i++) {
            String reportId = reportIds.get(i);
            Map<String, Object> r = reports.get(i);
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

    private Long getTimestamp(Map<String, Object> report) {
        Object ts = report.get("timestamp");
        if (ts instanceof Number) return ((Number) ts).longValue();
        return 0L;
    }

    public void cleanupOldReports(int maxReports) {
        try {
            long count = reportJpaRepository.count();
            if (count <= maxReports) return;

            List<ReportEntity> sorted = reportJpaRepository.findAllOrderByTimestampDesc();
            int toRemove = (int) (count - maxReports);
            
            for (int i = sorted.size() - 1; i >= sorted.size() - toRemove; i--) {
                String id = sorted.get(i).getId();
                reportJpaRepository.deleteById(id);
                removeShareByReportId(id);
            }
            log.info("自动清理 {} 条旧报告，保留最近 {} 条", toRemove, maxReports);
        } catch (Exception e) {
            log.warn("清理报告失败（可能表尚未创建）: {}", e.getMessage());
        }
    }

    private Map<String, Object> entityToMap(ReportEntity entity) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("id", entity.getId());
        map.put("totalTestCases", entity.getTotalTestCases());
        map.put("passedTestCases", entity.getPassedTestCases());
        map.put("failedTestCases", entity.getFailedTestCases());
        map.put("executionTimeMs", entity.getExecutionTimeMs());
        map.put("timestamp", entity.getTimestamp());
        map.put("favorite", entity.getFavorite() != null && entity.getFavorite());
        map.put("note", entity.getNote());
        map.put("group", entity.getGroup());
        map.put("project", entity.getProject());
        map.put("module", entity.getModule());
        map.put("function", entity.getFunction());
        map.put("asyncTaskId", entity.getAsyncTaskId());

        if (entity.getSummaryJson() != null) {
            try {
                map.put("summary", objectMapper.readValue(entity.getSummaryJson(), new TypeReference<Map<String, Object>>() {}));
            } catch (Exception e) {
                log.warn("Failed to deserialize summary", e);
            }
        }

        if (entity.getEvaluationsJson() != null) {
            try {
                map.put("evaluations", objectMapper.readValue(entity.getEvaluationsJson(), new TypeReference<List<Map<String, Object>>>() {}));
            } catch (Exception e) {
                log.warn("Failed to deserialize evaluations", e);
            }
        }

        if (entity.getTagsJson() != null) {
            try {
                map.put("tags", objectMapper.readValue(entity.getTagsJson(), new TypeReference<List<String>>() {}));
            } catch (Exception e) {
                log.warn("Failed to deserialize tags", e);
            }
        }

        return map;
    }

    private Integer getIntValue(Map<String, Object> data, String... keys) {
        for (String key : keys) {
            Object value = data.get(key);
            if (value instanceof Number) {
                return ((Number) value).intValue();
            }
        }
        return null;
    }

    private Long getLongValue(Map<String, Object> data, String... keys) {
        for (String key : keys) {
            Object value = data.get(key);
            if (value instanceof Number) {
                return ((Number) value).longValue();
            }
        }
        return null;
    }
}