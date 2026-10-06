package io.github.panris.agenteval.service;

import io.github.panris.agenteval.model.ReportEntity;
import io.github.panris.agenteval.repository.ReportJpaRepository;
import io.github.panris.agenteval.web.dto.ApiResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 报告写操作：保存、删除、复制、收藏、标签、笔记。
 */
@Service
public class ReportCommandService {

    private static final Logger log = LoggerFactory.getLogger(ReportCommandService.class);

    private final ReportJpaRepository reportJpaRepository;
    private final ShareService shareService;
    private final ObjectMapper objectMapper;

    public ReportCommandService(ReportJpaRepository reportJpaRepository, ShareService shareService) {
        this.reportJpaRepository = reportJpaRepository;
        this.shareService = shareService;
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

            if (evaluations instanceof List<?> evals) {
                int total = evals.size();
                int passed = (int) evals.stream()
                        .filter(e -> e instanceof Map<?, ?> m && "passed".equalsIgnoreCase(String.valueOf(m.get("status"))))
                        .count();
                entity.setTotalTestCases(total);
                entity.setPassedTestCases(passed);
                entity.setFailedTestCases(total - passed);
            }
        }

        Object execTime = report.get("executionTimeMs");
        if (execTime instanceof Number) {
            entity.setExecutionTimeMs(((Number) execTime).longValue());
        }

        Object timestamp = report.get("timestamp");
        if (timestamp instanceof Number) {
            entity.setTimestamp(((Number) timestamp).longValue());
        } else if (entity.getTimestamp() == null) {
            entity.setTimestamp(System.currentTimeMillis());
        }

        Object tags = report.get("tags");
        if (tags != null) {
            try {
                entity.setTagsJson(objectMapper.writeValueAsString(tags));
            } catch (Exception e) {
                log.warn("Failed to serialize tags", e);
            }
        }

        Object group = report.get("group");
        if (group != null) entity.setGroup(group.toString());

        Object project = report.get("project");
        if (project != null) entity.setProject(project.toString());

        Object module = report.get("module");
        if (module != null) entity.setModule(module.toString());

        Object function = report.get("function");
        if (function != null) entity.setFunction(function.toString());

        Object note = report.get("note");
        if (note != null) entity.setNote(note.toString());

        Object asyncTaskId = report.get("asyncTaskId");
        if (asyncTaskId != null) entity.setAsyncTaskId(asyncTaskId.toString());

        reportJpaRepository.save(entity);
    }

    public Map<String, Object> deleteReport(String reportId) {
        if (!reportJpaRepository.existsById(reportId)) {
            return ApiResponse.error("报告不存在");
        }
        reportJpaRepository.deleteById(reportId);
        shareService.removeShareByReportId(reportId);
        return ApiResponse.success("message", "报告已删除");
    }

    public Map<String, Object> clearAllReports() {
        reportJpaRepository.deleteAll();
        shareService.deleteAll();
        return ApiResponse.success("message", "所有报告已清除");
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

    public void cleanupOldReports(int maxReports) {
        try {
            long count = reportJpaRepository.count();
            if (count <= maxReports) return;

            List<ReportEntity> sorted = reportJpaRepository.findAllOrderByTimestampDesc();
            int toRemove = (int) (count - maxReports);

            for (int i = sorted.size() - 1; i >= sorted.size() - toRemove; i--) {
                String id = sorted.get(i).getId();
                reportJpaRepository.deleteById(id);
                shareService.removeShareByReportId(id);
            }
            log.info("自动清理 {} 条旧报告，保留最近 {} 条", toRemove, maxReports);
        } catch (Exception e) {
            log.warn("清理报告失败（可能表尚未创建）: {}", e.getMessage());
        }
    }
}
