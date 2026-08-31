package com.campus.secondhand.governance;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity @Table(name="report_actions")
class ReportAction {
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
    @Column(name="report_id",nullable=false) private Long reportId;
    @Column(name="admin_id",nullable=false) private Long adminId;
    @Enumerated(EnumType.STRING) @Column(name="result_status",nullable=false,length=20) private ReportStatus resultStatus;
    @Enumerated(EnumType.STRING) @Column(name="action_type",nullable=false,length=30) private GovernanceAction actionType;
    @Enumerated(EnumType.STRING) @Column(name="action_state",nullable=false,length=20) private ActionState actionState;
    @Column(name="event_id",length=80) private String eventId;
    @Column(nullable=false,length=1000) private String note;
    @Column(name="created_at",nullable=false,updatable=false) private LocalDateTime createdAt;
    Long getReportId(){return reportId;} void setReportId(Long v){reportId=v;} Long getAdminId(){return adminId;} void setAdminId(Long v){adminId=v;}
    ReportStatus getResultStatus(){return resultStatus;} void setResultStatus(ReportStatus v){resultStatus=v;} GovernanceAction getActionType(){return actionType;} void setActionType(GovernanceAction v){actionType=v;}
    ActionState getActionState(){return actionState;} void setActionState(ActionState v){actionState=v;} String getEventId(){return eventId;} void setEventId(String v){eventId=v;}
    String getNote(){return note;} void setNote(String v){note=v;} LocalDateTime getCreatedAt(){return createdAt;} void setCreatedAt(LocalDateTime v){createdAt=v;}
}
