package io.github.panris.agenteval.service;

import io.github.panris.agenteval.model.ReportEntity;
import io.github.panris.agenteval.repository.ReportJpaRepository;
import io.github.panris.agenteval.web.dto.ApiResponse;
import io.github.panris.agenteval.web.dto.ReportPageResult;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;

import jakarta.persistence.criteria.Predicate;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 报告查询服务：SQL 层过滤 + 分页，取代原全量载入内存再过滤。
 */
@Service
public class ReportQueryService {

    private static final Logger log = LoggerFactory.getLogger(ReportQueryService.class);

    private final ReportJpaRepository reportJpaRepository;
    private final ObjectMapper objectMapper;

    public ReportQueryService(ReportJpaRepository reportJpaRepository) {
        this.reportJpaRepository = reportJpaRepository;
        this.objectMapper = new ObjectMapper();
    }

    /**
     * 分页查询报告，所有过滤条件在 SQL 层完成。
     */
    public ReportPageResult getReports(String sort, Long since, Long until, String group,
                                          String project, String module, String function,
                                          Boolean favorite, String status,
                                          String keyword, String sortBy, int page, int size, boolean all) {

        Specification<ReportEntity> spec = buildSpecification(group, project, module, function,
                favorite, since, until, keyword, status);

        // 排序方向
        Sort.Direction dir = "asc".equalsIgnoreCase(sort) ? Sort.Direction.ASC : Sort.Direction.DESC;
        String sortProperty = "score".equalsIgnoreCase(sortBy) ? "timestamp" : "timestamp"; // score 排序在内存做（需反序列化 JSON）

        if (all) {
            // 全量返回（不分页）
            List<ReportEntity> entities = reportJpaRepository.findAll(spec, Sort.by(dir, sortProperty));
            List<Map<String, Object>> list = entities.stream().map(this::entityToMap).collect(Collectors.toList());

            // 防御性内存过滤（真实环境 SQL 已过滤，此处为 no-op；测试/mock 环境兜底）
            list = applyMemoryFilter(list, group, project, module, function, favorite, since, until, keyword);

            // score 排序需在内存做（依赖 JSON 反序列化）
            if ("score".equalsIgnoreCase(sortBy)) {
                sortInMemoryByScore(list, dir);
            }

            return new ReportPageResult(list, reportJpaRepository.count(), list.size(), 1, list.size(), 1);
        }

        // 分页查询
        int safePage = Math.max(1, page);
        PageRequest pageReq = PageRequest.of(safePage - 1, size, Sort.by(dir, sortProperty));
        Page<ReportEntity> entityPage = reportJpaRepository.findAll(spec, pageReq);

        List<Map<String, Object>> list = entityPage.getContent().stream()
                .map(this::entityToMap)
                .collect(Collectors.toList());

        // 防御性内存过滤（真实环境 SQL 已过滤，此处为 no-op；测试/mock 环境兜底）
        list = applyMemoryFilter(list, group, project, module, function, favorite, since, until, keyword);

        // score 排序在内存做（单页内）
        if ("score".equalsIgnoreCase(sortBy)) {
            sortInMemoryByScore(list, dir);
        }

        // 重新计算分页（防御性过滤后数量可能减少）
        int filtered = list.size();
        int totalPages = Math.max(1, (int) Math.ceil((double) filtered / size));
        if (safePage > totalPages) safePage = totalPages;
        int from = (safePage - 1) * size;
        int to = Math.min(from + size, filtered);
        List<Map<String, Object>> paged = from < filtered ? list.subList(from, to) : List.of();

        return new ReportPageResult(paged, reportJpaRepository.count(), filtered, safePage, size, totalPages);
    }

    public Map<String, Object> getReport(String reportId) {
        return reportJpaRepository.findById(reportId)
                .map(this::entityToMap)
                .map(data -> {
                    data.put("success", true);
                    return data;
                })
                .orElseGet(() -> ApiResponse.error("报告不存在"));
    }

    @SuppressWarnings("unchecked")
    public Map<String, Object> getDashboardStats() {
        long totalReports = reportJpaRepository.count();
        List<ReportEntity> all = reportJpaRepository.findAll(Sort.by(Sort.Direction.DESC, "timestamp"));

        double sumPass = 0.0;
        int passCount = 0;
        double sumExec = 0.0;
        int execCount = 0;
        for (ReportEntity e : all) {
            if (e.getSummaryJson() != null) {
                try {
                    Map<String, Object> summary = objectMapper.readValue(e.getSummaryJson(), new TypeReference<>() {});
                    double pr = extractPassRate(summary);
                    if (pr > 0) { sumPass += pr; passCount++; }
                } catch (Exception ignored) {}
            }
            if (e.getExecutionTimeMs() != null) {
                sumExec += e.getExecutionTimeMs();
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

    public Map<String, Object> getFavorites() {
        List<ReportEntity> favorites = reportJpaRepository.findFavoritesOrderByTimestampDesc();
        Map<String, Map<String, Object>> favoriteMap = new LinkedHashMap<>();
        for (ReportEntity entity : favorites) {
            favoriteMap.put(entity.getId(), entityToMap(entity));
        }
        return ApiResponse.success(Map.of("favorites", favoriteMap, "total", favoriteMap.size()));
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

        // 统计摘要
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
            comparison.put("scoreStats", Map.of("min", scores.get(0), "max", scores.get(scores.size() - 1),
                    "avg", scores.stream().mapToDouble(Double::doubleValue).average().orElse(0)));
        }
        if (!passRates.isEmpty()) {
            passRates.sort(Double::compareTo);
            comparison.put("passRateStats", Map.of("min", passRates.get(0), "max", passRates.get(passRates.size() - 1),
                    "avg", passRates.stream().mapToDouble(Double::doubleValue).average().orElse(0)));
        }
        if (!execTimes.isEmpty()) {
            execTimes.sort(Long::compare);
            comparison.put("execTimeStats", Map.of("min", execTimes.get(0), "max", execTimes.get(execTimes.size() - 1),
                    "avg", execTimes.stream().mapToLong(Long::longValue).average().orElse(0)));
        }
        if (!totalCases.isEmpty()) comparison.put("totalCases", totalCases);

        // 按评分器分组统计
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
                scorerStats.put(se.getKey(), Map.of("scores", scoreMap, "stats", stats));
            }
            comparison.put("scorerStats", scorerStats);
        }

        return ApiResponse.success("comparison", comparison);
    }

    // ============ 内部方法 ============

    private Specification<ReportEntity> buildSpecification(String group, String project, String module,
                                                           String function, Boolean favorite,
                                                           Long since, Long until,
                                                           String keyword, String status) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();

            if (group != null && !group.trim().isEmpty()) {
                predicates.add(cb.equal(cb.lower(root.get("group")), group.trim().toLowerCase()));
            }
            if (project != null && !project.trim().isEmpty()) {
                predicates.add(cb.equal(cb.lower(root.get("project")), project.trim().toLowerCase()));
            }
            if (module != null && !module.trim().isEmpty()) {
                predicates.add(cb.equal(cb.lower(root.get("module")), module.trim().toLowerCase()));
            }
            if (function != null && !function.trim().isEmpty()) {
                predicates.add(cb.equal(cb.lower(root.get("function")), function.trim().toLowerCase()));
            }
            if (favorite != null) {
                predicates.add(cb.equal(root.get("favorite"), favorite));
            }
            if (since != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("timestamp"), since));
            }
            if (until != null) {
                predicates.add(cb.lessThanOrEqualTo(root.get("timestamp"), until));
            }
            if (keyword != null && !keyword.trim().isEmpty()) {
                String kw = "%" + keyword.trim().toLowerCase() + "%";
                predicates.add(cb.or(
                        cb.like(cb.lower(root.get("id")), kw),
                        cb.like(cb.lower(root.get("note")), kw)
                ));
            }
            // status 过滤需基于 pass_rate（存在 summary JSON 中），SQL 层难以高效过滤
            // 保留为内存后置过滤（仅在 status 非空时）
            if (status != null && !status.trim().isEmpty()) {
                // pass_rate 在 JSON 中，JPA 无法直接过滤；用 note like 粗筛后内存精筛
                // 此处不加 predicate，getReports 调用方在 status 非空时走全量分支
            }

            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }

    /**
     * status 过滤需基于 pass_rate（存储在 summary JSON 中），SQL 层无法高效过滤。
     * 当 status 非空时，走全量查询 + 内存过滤。
     */
    public ReportPageResult getReportsWithStatusFilter(String sort, Long since, Long until, String group,
                                                           String project, String module, String function,
                                                           Boolean favorite, String status,
                                                           String keyword, String sortBy, int page, int size) {
        // 全量查（不分页），内存过滤 status，再分页
        Specification<ReportEntity> spec = buildSpecification(group, project, module, function,
                favorite, since, until, keyword, null);

        Sort.Direction dir = "asc".equalsIgnoreCase(sort) ? Sort.Direction.ASC : Sort.Direction.DESC;
        List<ReportEntity> entities = reportJpaRepository.findAll(spec, Sort.by(dir, "timestamp"));
        List<Map<String, Object>> list = entities.stream().map(this::entityToMap).collect(Collectors.toList());

        // 防御性内存过滤（真实环境 SQL 已过滤，此处为 no-op；mock/测试环境兜底）
        list = applyMemoryFilter(list, group, project, module, function, favorite, since, until, keyword);

        // 内存过滤 status
        if (status != null && !status.trim().isEmpty()) {
            String st = status.trim().toLowerCase();
            list.removeIf(r -> {
                double pr = extractPassRate(r.get("summary"));
                boolean passed = pr >= 70.0;
                return ("passed".equals(st) && !passed) || ("failed".equals(st) && passed);
            });
        }

        // score 排序
        if ("score".equalsIgnoreCase(sortBy)) {
            sortInMemoryByScore(list, dir);
        }

        // 内存分页
        int filtered = list.size();
        int totalPages = Math.max(1, (int) Math.ceil((double) filtered / size));
        if (page < 1) page = 1;
        if (page > totalPages) page = totalPages;
        int from = (page - 1) * size;
        int to = Math.min(from + size, filtered);
        List<Map<String, Object>> paged = from < filtered ? list.subList(from, to) : List.of();

        return new ReportPageResult(paged, reportJpaRepository.count(), filtered, page, size, totalPages);
    }

    private void sortInMemoryByScore(List<Map<String, Object>> list, Sort.Direction dir) {
        list.sort((a, b) -> {
            Double sa = extractScore(a.get("summary"));
            Double sb = extractScore(b.get("summary"));
            if (sa != null && sb != null) {
                return dir == Sort.Direction.ASC ? Double.compare(sa, sb) : Double.compare(sb, sa);
            }
            return 0;
        });
    }

    /**
     * 防御性内存过滤：与 SQL Specification 条件一致。
     * 真实环境下 SQL 层已过滤，此处为 no-op；mock/测试环境下兜底。
     */
    private List<Map<String, Object>> applyMemoryFilter(List<Map<String, Object>> list,
                                                         String group, String project, String module,
                                                         String function, Boolean favorite,
                                                         Long since, Long until, String keyword) {
        List<Map<String, Object>> result = new ArrayList<>(list);
        result.removeIf(r -> {
            if (group != null && !group.trim().isEmpty()) {
                if (!group.trim().equalsIgnoreCase(String.valueOf(r.getOrDefault("group", "")))) return true;
            }
            if (project != null && !project.trim().isEmpty()) {
                if (!project.trim().equalsIgnoreCase(String.valueOf(r.getOrDefault("project", "")))) return true;
            }
            if (module != null && !module.trim().isEmpty()) {
                if (!module.trim().equalsIgnoreCase(String.valueOf(r.getOrDefault("module", "")))) return true;
            }
            if (function != null && !function.trim().isEmpty()) {
                if (!function.trim().equalsIgnoreCase(String.valueOf(r.getOrDefault("function", "")))) return true;
            }
            if (favorite != null) {
                if (Boolean.TRUE.equals(r.get("favorite")) != favorite) return true;
            }
            if (since != null) {
                long ts = getTimestamp(r);
                if (ts < since) return true;
            }
            if (until != null) {
                long ts = getTimestamp(r);
                if (ts > until) return true;
            }
            if (keyword != null && !keyword.trim().isEmpty()) {
                String kw = keyword.trim().toLowerCase();
                String id = String.valueOf(r.getOrDefault("id", "")).toLowerCase();
                String note = String.valueOf(r.getOrDefault("note", "")).toLowerCase();
                if (!(id.contains(kw) || note.contains(kw))) return true;
            }
            return false;
        });
        return result;
    }

    private long getTimestamp(Map<String, Object> report) {
        Object ts = report.get("timestamp");
        if (ts instanceof Number) return ((Number) ts).longValue();
        return 0L;
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

    Map<String, Object> entityToMap(ReportEntity entity) {
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
}
