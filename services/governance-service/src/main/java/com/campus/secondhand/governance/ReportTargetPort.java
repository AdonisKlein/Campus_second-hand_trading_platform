package com.campus.secondhand.governance;

interface ReportTargetPort {
    TargetSnapshot resolve(ReportTargetType type,long targetId);
    record TargetSnapshot(long targetId,long reportedUserId,String summary,ReportTargetType targetType,boolean reportable) {}
}
