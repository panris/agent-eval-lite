package io.github.panris.agenteval.service;

import io.github.panris.agenteval.model.ReportEntity;
import io.github.panris.agenteval.repository.ReportJpaRepository;
import io.github.panris.agenteval.repository.SharedReportJpaRepository;
import io.github.panris.agenteval.web.dto.ApiResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.*;

/**
 * 报告服务门面：委托给 ReportQueryService（读）、ReportCommandService（写）、ShareService（分享）。
 * 保留此类是为了不破坏现有调用方（Controller / AsyncEvalService / HealthController 等）。
 */
@Service
public class ReportService {

    private static final Logger log = LoggerFactory.getLogger(ReportService.class);

    private final ReportQueryService queryService;
    private final ReportCommandService commandService;
    private final ShareService shareService;
    // 保留直接引用以兼容 HealthController 的 getAllReports(total) 调用
    private final ReportJpaRepository reportJpaRepository;

    public ReportService(ReportJpaRepository reportJpaRepository,
                         SharedReportJpaRepository sharedReportJpaRepository) {
        this.shareService = new ShareService(sharedReportJpaRepository);
        this.queryService = new ReportQueryService(reportJpaRepository);
        this.commandService = new ReportCommandService(reportJpaRepository, shareService);
        this.reportJpaRepository = reportJpaRepository;
    }

    // ============ 查询委托 ============

    public Map<String, Object> getAllReports(String sort, Long since, Long until, String group,
                                              String project, String module, String function,
                                              Boolean favorite, String status,
                                              String keyword, String sortBy, int page, int size, boolean all) {
        // status 过滤需基于 pass_rate（JSON 内），走专用分支
        if (status != null && !status.trim().isEmpty() && !all) {
            return queryService.getReportsWithStatusFilter(sort, since, until, group, project, module,
                    function, favorite, status, keyword, sortBy, page, size);
        }
        return queryService.getReports(sort, since, until, group, project, module, function,
                favorite, status, keyword, sortBy, page, size, all);
    }

    public Map<String, Object> getReport(String reportId) {
        return queryService.getReport(reportId);
    }

    public Map<String, Object> getDashboardStats() {
        return queryService.getDashboardStats();
    }

    public Map<String, Object> getFavorites() {
        return queryService.getFavorites();
    }

    public Map<String, Object> compareReports(List<String> reportIds) {
        return queryService.compareReports(reportIds);
    }

    // ============ 写操作委托 ============

    public void saveReport(String reportId, Map<String, Object> report) {
        commandService.saveReport(reportId, report);
    }

    public Map<String, Object> deleteReport(String reportId) {
        return commandService.deleteReport(reportId);
    }

    public Map<String, Object> clearAllReports() {
        return commandService.clearAllReports();
    }

    public Map<String, Object> copyReport(String reportId) {
        return commandService.copyReport(reportId);
    }

    public Map<String, Object> toggleFavorite(String reportId) {
        return commandService.toggleFavorite(reportId);
    }

    public Map<String, Object> updateTags(String reportId, List<String> tags) {
        return commandService.updateTags(reportId, tags);
    }

    public Map<String, Object> updateNote(String reportId, String note) {
        return commandService.updateNote(reportId, note);
    }

    public void cleanupOldReports(int maxReports) {
        commandService.cleanupOldReports(maxReports);
    }

    // ============ 分享委托 ============

    public Map<String, Object> createShareLink(String reportId) {
        return shareService.createShareLink(reportId, reportJpaRepository.existsById(reportId));
    }

    public String resolveShareId(String shareId) {
        return shareService.resolveShareId(shareId);
    }
}
