package com.campus.secondhand.governance;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name="content_reports", uniqueConstraints=@UniqueConstraint(name="uq_reporter_target", columnNames={"reporter_id","target_type","target_id"}))
class ContentReport {
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
    @Column(name="reporter_id",nullable=false) private Long reporterId;
    @Column(name="reporter_name",nullable=false,length=80) private String reporterName;
    @Enumerated(EnumType.STRING) @Column(name="target_type",nullable=false,length=20) private ReportTargetType targetType;
    @Column(name="target_id",nullable=false) private Long targetId;
    @Column(name="reported_user_id",nullable=false) private Long reportedUserId;
    @Column(name="target_summary",nullable=false,length=500) private String targetSummary;
    @Enumerated(EnumType.STRING) @Column(name="reason_code",nullable=false,length=40) private ReportReason reasonCode;
    @Column(nullable=false,length=1000) private String description;
    @Enumerated(EnumType.STRING) @Column(nullable=false,length=20) private ReportStatus status=ReportStatus.OPEN;
    @Enumerated(EnumType.STRING) @Column(name="decision_action",nullable=false,length=30) private GovernanceAction decisionAction=GovernanceAction.NONE;
    @Enumerated(EnumType.STRING) @Column(name="action_state",nullable=false,length=20) private ActionState actionState=ActionState.NONE;
    @Column(name="action_event_id",length=80) private String actionEventId;
    @Column(name="action_error",length=500) private String actionError;
    @Column(name="resolution_note",length=1000) private String resolutionNote;
    @Column(name="handled_by") private Long handledBy;
    @Column(name="resolved_at") private LocalDateTime resolvedAt;
    @Column(name="created_at",nullable=false,updatable=false) private LocalDateTime createdAt;
    @Column(name="updated_at",nullable=false) private LocalDateTime updatedAt;
    @Version @Column(nullable=false) private Long version=0L;
    Long getId(){return id;} void setId(Long v){id=v;} Long getReporterId(){return reporterId;} void setReporterId(Long v){reporterId=v;}
    String getReporterName(){return reporterName;} void setReporterName(String v){reporterName=v;} ReportTargetType getTargetType(){return targetType;} void setTargetType(ReportTargetType v){targetType=v;}
    Long getTargetId(){return targetId;} void setTargetId(Long v){targetId=v;} Long getReportedUserId(){return reportedUserId;} void setReportedUserId(Long v){reportedUserId=v;}
    String getTargetSummary(){return targetSummary;} void setTargetSummary(String v){targetSummary=v;} ReportReason getReasonCode(){return reasonCode;} void setReasonCode(ReportReason v){reasonCode=v;}
    String getDescription(){return description;} void setDescription(String v){description=v;} ReportStatus getStatus(){return status;} void setStatus(ReportStatus v){status=v;}
    GovernanceAction getDecisionAction(){return decisionAction;} void setDecisionAction(GovernanceAction v){decisionAction=v;} ActionState getActionState(){return actionState;} void setActionState(ActionState v){actionState=v;}
    String getActionEventId(){return actionEventId;} void setActionEventId(String v){actionEventId=v;}
    String getActionError(){return actionError;} void setActionError(String v){actionError=v;} String getResolutionNote(){return resolutionNote;} void setResolutionNote(String v){resolutionNote=v;}
    Long getHandledBy(){return handledBy;} void setHandledBy(Long v){handledBy=v;} LocalDateTime getResolvedAt(){return resolvedAt;} void setResolvedAt(LocalDateTime v){resolvedAt=v;}
    LocalDateTime getCreatedAt(){return createdAt;} void setCreatedAt(LocalDateTime v){createdAt=v;} LocalDateTime getUpdatedAt(){return updatedAt;} void setUpdatedAt(LocalDateTime v){updatedAt=v;}
}
