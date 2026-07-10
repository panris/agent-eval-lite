package io.github.panris.agenteval.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;

/**
 * 报告自动清理服务。
 * - 每天凌晨 3 点执行，清理超过 30 天的报告。
 * - 同时限制报告总数不超过 500 条（按时间倒序保留）。
 */
@Service
public class ReportCleanupService {
    private static final Logger log = LoggerFactory.getLogger(ReportCleanupService.class);

    private final ReportService reportService;

    private static final int MAX_REPORTS = 500;
    private static final int MAX_AGE_DAYS = 30;

    public ReportCleanupService(ReportService reportService) {
        this.reportService = reportService;
    }

    @PostConstruct
    public void onStartup() {
        // 启动时执行一次保守清理（仅限制总数，不按时间删除）
        try {
            reportService.cleanupOldReports(MAX_REPORTS);
        } catch (Exception e) {
            log.warn("启动时清理报告失败: {}", e.getMessage());
        }
    }

    /**
     * 每天凌晨 3 点执行完整清理。
     * cron 表达式：秒 分 时 日 月 周（周字段省略表示每天）
     */
    @Scheduled(cron = "0 0 3 * * ?", zone = "Asia/Shanghai")
    public void scheduledCleanup() {
        log.info("开始每日报告清理...");
        try {
            reportService.cleanupOldReports(MAX_REPORTS);
        } catch (Exception e) {
            log.error("每日报告清理失败: {}", e.getMessage(), e);
        }
    }
}
