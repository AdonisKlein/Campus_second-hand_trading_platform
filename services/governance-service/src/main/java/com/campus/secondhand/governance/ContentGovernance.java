package com.campus.secondhand.governance;

import java.time.LocalDateTime;
import java.util.List;

public interface ContentGovernance {
    ReportView submit(CurrentActor actor, ReportDraft draft);
    ReportPage listMine(CurrentActor actor, int page, int size);
    ReportPage listForAdmin(CurrentActor actor, ReportStatus status, int page, int size);
    ReportView decide(CurrentActor actor, long reportId, Decision decision);
    ReportView applyActionResult(ActionResult result);

    record ReportDraft(ReportTargetType targetType,Long targetId,ReportReason reasonCode,String description) {}
    record Decision(ReportStatus status,GovernanceAction action,String note) {}
    record ActionResult(String eventId,String commandEventId,String correlationId,String type,long reportId,String reason) {}
    record ReportPage(List<ReportView> reports,int page,int size,boolean hasNext) {}
    record AuditView(Long adminId,ReportStatus resultStatus,GovernanceAction action,ActionState actionState,String note,LocalDateTime createdAt) {}
    record ReportView(Long id,Long reporterId,String reporterName,ReportTargetType targetType,Long targetId,
                      Long reportedUserId,String targetSummary,ReportReason reasonCode,String description,
                      ReportStatus status,GovernanceAction decisionAction,ActionState actionState,String actionError,
                      String resolutionNote,LocalDateTime createdAt,LocalDateTime resolvedAt,List<AuditView> history) {}
}
