package com.campus.secondhand.report;

import java.time.LocalDateTime;
import java.util.List;

public interface ContentGovernance {
    ReportView submit(Long reporterId, ReportDraft draft);
    ReportPage listMine(Long reporterId, int page, int size);
    ReportPage listForAdmin(ReportStatus status, int page, int size);
    ReportView decide(Long adminId, Long reportId, Decision decision);

    record ReportDraft(ReportTargetType targetType, Long targetId, ReportReason reasonCode, String description) {}
    record Decision(ReportStatus status, GovernanceAction action, String note) {}
    record ReportPage(List<ReportView> reports, int page, int size, boolean hasNext) {}
    record AuditView(Long adminId, ReportStatus resultStatus, GovernanceAction action, String note, LocalDateTime createdAt) {}
    record ReportView(Long id, Long reporterId, String reporterName, ReportTargetType targetType, Long targetId,
                      Long reportedUserId, String targetSummary, ReportReason reasonCode, String description,
                      ReportStatus status, GovernanceAction decisionAction, String resolutionNote,
                      LocalDateTime createdAt, LocalDateTime resolvedAt, List<AuditView> history) {}
}
