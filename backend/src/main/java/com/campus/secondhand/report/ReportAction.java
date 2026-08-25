package com.campus.secondhand.report;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "report_actions")
public class ReportAction {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(name = "report_id", nullable = false) private Long reportId;
    @Column(name = "admin_id", nullable = false) private Long adminId;
    @Enumerated(EnumType.STRING) @Column(name = "result_status", nullable = false, length = 20) private ReportStatus resultStatus;
    @Enumerated(EnumType.STRING) @Column(name = "action_type", nullable = false, length = 30) private GovernanceAction actionType;
    @Column(nullable = false, length = 1000) private String note;
    @Column(name = "created_at", nullable = false, updatable = false) private LocalDateTime createdAt = LocalDateTime.now();

    public Long getId() { return id; }
    public Long getReportId() { return reportId; }
    public void setReportId(Long value) { reportId = value; }
    public Long getAdminId() { return adminId; }
    public void setAdminId(Long value) { adminId = value; }
    public ReportStatus getResultStatus() { return resultStatus; }
    public void setResultStatus(ReportStatus value) { resultStatus = value; }
    public GovernanceAction getActionType() { return actionType; }
    public void setActionType(GovernanceAction value) { actionType = value; }
    public String getNote() { return note; }
    public void setNote(String value) { note = value; }
    public LocalDateTime getCreatedAt() { return createdAt; }
}
