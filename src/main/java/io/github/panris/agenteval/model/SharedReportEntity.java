package io.github.panris.agenteval.model;

import jakarta.persistence.*;

@Entity
@Table(name = "shared_reports")
public class SharedReportEntity {

    @Id
    @Column(length = 8)
    private String shareId;

    @Column(name = "report_id", length = 50)
    private String reportId;

    public SharedReportEntity() {
    }

    public SharedReportEntity(String shareId, String reportId) {
        this.shareId = shareId;
        this.reportId = reportId;
    }

    public String getShareId() {
        return shareId;
    }

    public void setShareId(String shareId) {
        this.shareId = shareId;
    }

    public String getReportId() {
        return reportId;
    }

    public void setReportId(String reportId) {
        this.reportId = reportId;
    }
}