package io.github.panris.agenteval.service;

import io.github.panris.agenteval.model.SharedReportEntity;
import io.github.panris.agenteval.repository.SharedReportJpaRepository;
import io.github.panris.agenteval.web.dto.ApiResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 分享链接管理：创建、解析、级联删除。
 */
@Service
public class ShareService {

    private static final Logger log = LoggerFactory.getLogger(ShareService.class);

    private final SharedReportJpaRepository sharedReportJpaRepository;

    public ShareService(SharedReportJpaRepository sharedReportJpaRepository) {
        this.sharedReportJpaRepository = sharedReportJpaRepository;
    }

    public Map<String, Object> createShareLink(String reportId, boolean reportExists) {
        if (!reportExists) {
            return ApiResponse.error("报告不存在");
        }
        String shareId = UUID.randomUUID().toString().substring(0, 8);
        sharedReportJpaRepository.save(new SharedReportEntity(shareId, reportId));
        return ApiResponse.success(Map.of("shareId", shareId, "url", "/share/" + shareId));
    }

    public String resolveShareId(String shareId) {
        return sharedReportJpaRepository.findById(shareId)
                .map(SharedReportEntity::getReportId)
                .orElse(null);
    }

    public void removeShareByReportId(String reportId) {
        List<SharedReportEntity> shares = sharedReportJpaRepository.findByReportId(reportId);
        sharedReportJpaRepository.deleteAll(shares);
    }

    public void deleteAll() {
        sharedReportJpaRepository.deleteAll();
    }
}
