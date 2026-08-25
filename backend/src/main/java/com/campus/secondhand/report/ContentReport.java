package com.campus.secondhand.report;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "content_reports", uniqueConstraints = @UniqueConstraint(name = "uq_reporter_target",
    columnNames = {"reporter_id", "target_type", "target_id"}))
public class ContentReport {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(name = "reporter_id", nullable = false) private Long reporterId;
    @Enumerated(EnumType.STRING) @Column(name = "target_type", nullable = false, length = 20) private ReportTargetType targetType;
    @Column(name = "target_id", nullable = false) private Long targetId;
    @Column(name = "reported_user_id", nullable = false) private Long reportedUserId;
    @Column(name = "target_summary", nullable = false, length = 500) private String targetSummary;
    @Enumerated(EnumType.STRING) @Column(name = "reason_code", nullable = false, length = 40) private ReportReason reasonCode;
    @Column(nullable = false, length = 1000) private String description;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 20) private ReportStatus status = ReportStatus.OPEN;
    @Enumerated(EnumType.STRING) @Column(name = "decision_action", length = 30) private GovernanceAction decisionAction;
    @Column(name = "resolution_note", length = 1000) private String resolutionNote;
    @Column(name = "handled_by") private Long handledBy;
    @Column(name = "resolved_at") private LocalDateTime resolvedAt;
    @Column(name = "created_at", nullable = false, updatable = false) private LocalDateTime createdAt = LocalDateTime.now();
    @Column(name = "updated_at", nullable = false) private LocalDateTime updatedAt = LocalDateTime.now();
    @Version private Long version;

    public Long getId() { return id; }
    public Long getReporterId() { return reporterId; }
    public void setReporterId(Long value) { reporterId = value; }
    public ReportTargetType getTargetType() { return targetType; }
    public void setTargetType(ReportTargetType value) { targetType = value; }
    public Long getTargetId() { return targetId; }
    public void setTargetId(Long value) { targetId = value; }
    public Long getReportedUserId() { return reportedUserId; }
    public void setReportedUserId(Long value) { reportedUserId = value; }
    public String getTargetSummary() { return targetSummary; }
    public void setTargetSummary(String value) { targetSummary = value; }
    public ReportReason getReasonCode() { return reasonCode; }
    public void setReasonCode(ReportReason value) { reasonCode = value; }
    public String getDescription() { return description; }
    public void setDescription(String value) { description = value; }
    public ReportStatus getStatus() { return status; }
    public void setStatus(ReportStatus value) { status = value; }
    public GovernanceAction getDecisionAction() { return decisionAction; }
    public void setDecisionAction(GovernanceAction value) { decisionAction = value; }
    public String getResolutionNote() { return resolutionNote; }
    public void setResolutionNote(String value) { resolutionNote = value; }
    public Long getHandledBy() { return handledBy; }
    public void setHandledBy(Long value) { handledBy = value; }
    public LocalDateTime getResolvedAt() { return resolvedAt; }
    public void setResolvedAt(LocalDateTime value) { resolvedAt = value; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime value) { updatedAt = value; }
}
