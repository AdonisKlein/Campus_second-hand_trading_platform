package com.campus.secondhand.governance;

import java.time.*;
import java.util.*;
import java.util.stream.Collectors;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ContentGovernanceService implements ContentGovernance {
    private final ContentReportRepository reports;private final ReportActionRepository actions;private final GovernanceInboxRepository inbox;
    private final AccountGovernancePort accounts;private final ReportTargetPort targets;private final GovernanceEventStore events;private final Clock clock;
    public ContentGovernanceService(ContentReportRepository reports,ReportActionRepository actions,GovernanceInboxRepository inbox,
                                    AccountGovernancePort accounts,ReportTargetPort targets,GovernanceEventStore events,Clock clock){this.reports=reports;this.actions=actions;this.inbox=inbox;this.accounts=accounts;this.targets=targets;this.events=events;this.clock=clock;}

    @Override @Transactional
    public ReportView submit(CurrentActor actor,ReportDraft draft){
        if(actor==null||!actor.student())throw GovernanceException.forbidden("只有学生用户可以提交举报");
        AccountGovernancePort.AccountSnapshot reporter=accounts.requireActiveStudent(actor.userId());
        if(draft==null||draft.targetType()==null||draft.targetId()==null||draft.targetId()<1||draft.reasonCode()==null)throw new IllegalArgumentException("举报信息不完整");
        String description=normalize(draft.description(),10,1000,"请用 10—1000 个字说明举报原因");
        if(reports.countByReporterIdAndCreatedAtAfter(actor.userId(),now().minusHours(24))>=20)throw new GovernanceException(org.springframework.http.HttpStatus.TOO_MANY_REQUESTS,"RATE_LIMITED","24 小时内最多提交 20 条举报，请稍后再试");
        ReportTargetPort.TargetSnapshot target=targets.resolve(draft.targetType(),draft.targetId());
        if(target.targetType()!=draft.targetType()||actor.userId()==target.reportedUserId())throw GovernanceException.conflict("SELF_REPORT","不能举报自己发布的内容");
        if(reports.existsByReporterIdAndTargetTypeAndTargetId(actor.userId(),draft.targetType(),draft.targetId()))throw GovernanceException.conflict("DUPLICATE_REPORT","你已经举报过该内容，可在“我的举报”查看进度");
        ContentReport report=new ContentReport();report.setReporterId(actor.userId());report.setReporterName(reporter.displayName());report.setTargetType(draft.targetType());report.setTargetId(draft.targetId());report.setReportedUserId(target.reportedUserId());report.setTargetSummary(trim(target.summary(),500));report.setReasonCode(draft.reasonCode());report.setDescription(description);report.setCreatedAt(now());report.setUpdatedAt(now());
        try{return view(reports.saveAndFlush(report),List.of());}catch(DataIntegrityViolationException duplicate){throw GovernanceException.conflict("DUPLICATE_REPORT","你已经举报过该内容，可在“我的举报”查看进度");}
    }

    @Override @Transactional(readOnly=true) public ReportPage listMine(CurrentActor actor,int page,int size){if(actor==null||!actor.student())throw GovernanceException.forbidden("只有学生用户可以查看举报");accounts.requireActiveStudent(actor.userId());return page(reports.findByReporterIdOrderByCreatedAtDesc(actor.userId(),pageable(page,size)));}
    @Override @Transactional(readOnly=true) public ReportPage listForAdmin(CurrentActor actor,ReportStatus status,int page,int size){requireAdmin(actor);Pageable pageable=pageable(page,size);return page(status==null?reports.findAll(pageable):reports.findByStatusOrderByCreatedAtDesc(status,pageable));}

    @Override @Transactional
    public ReportView decide(CurrentActor actor,long reportId,Decision decision){requireAdmin(actor);ContentReport report=reports.findLockedById(reportId).orElseThrow(()->GovernanceException.notFound("举报不存在"));
        if(decision==null||decision.status()==null)throw new IllegalArgumentException("处理结果不合法");String note=normalize(decision.note(),2,1000,"请填写处理说明");
        boolean retry=report.getStatus()==ReportStatus.RESOLVED&&report.getActionState()==ActionState.FAILED;
        if(!retry&&report.getStatus()!=ReportStatus.OPEN)throw GovernanceException.conflict("ALREADY_DECIDED","该举报已经处理，不能重复操作");
        if(retry){if(decision.status()!=ReportStatus.RESOLVED||decision.action()!=report.getDecisionAction())throw GovernanceException.conflict("INVALID_RETRY","重试不能改变原治理决定");return dispatch(report,actor.userId(),note);}
        if(decision.status()==ReportStatus.DISMISSED){if(decision.action()!=null&&decision.action()!=GovernanceAction.NONE)throw GovernanceException.conflict("INVALID_DECISION","驳回举报不能执行治理措施");report.setStatus(ReportStatus.DISMISSED);report.setDecisionAction(GovernanceAction.NONE);report.setActionState(ActionState.NONE);report.setResolutionNote(note);report.setHandledBy(actor.userId());report.setResolvedAt(now());report.setUpdatedAt(now());ReportAction audit=audit(report,actor.userId(),ActionState.NONE,note,null);actions.save(audit);reports.save(report);return view(report,List.of(audit));}
        if(decision.status()!=ReportStatus.RESOLVED||decision.action()!=expected(report.getTargetType()))throw GovernanceException.conflict("INVALID_DECISION","治理措施与举报对象不匹配");
        report.setStatus(ReportStatus.RESOLVED);report.setDecisionAction(decision.action());report.setResolutionNote(note);report.setHandledBy(actor.userId());report.setResolvedAt(now());return dispatch(report,actor.userId(),note);
    }

    private ReportView dispatch(ContentReport report,long adminId,String note){report.setActionState(ActionState.PENDING);report.setActionError(null);report.setUpdatedAt(now());reports.saveAndFlush(report);String eventId=events.request(report,adminId);report.setActionEventId(eventId);ReportAction audit=audit(report,adminId,ActionState.PENDING,note,eventId);actions.save(audit);return view(report,List.of(audit));}

    @Override @Transactional
    public ReportView applyActionResult(ActionResult result){ContentReport report=reports.findLockedById(result.reportId()).orElseThrow(()->GovernanceException.notFound("举报不存在"));if(inbox.existsById(result.eventId()))return view(report,history(List.of(report)).getOrDefault(report.getId(),List.of()));
        inbox.saveAndFlush(new GovernanceInboxEvent(result.eventId(),result.type(),now()));if(!Objects.equals(report.getActionEventId(),result.correlationId()))return view(report,history(List.of(report)).getOrDefault(report.getId(),List.of()));
        ActionState state=switch(result.type()){case "GovernanceActionApplied"->ActionState.APPLIED;case "GovernanceActionFailed"->ActionState.FAILED;default->throw new IllegalArgumentException("未知治理结果事件");};report.setActionState(state);report.setActionError(state==ActionState.FAILED?trim(result.reason()==null?"治理措施执行失败":result.reason(),500):null);report.setUpdatedAt(now());ReportAction audit=audit(report,report.getHandledBy(),state,state==ActionState.APPLIED?"治理措施已生效":report.getActionError(),result.eventId());reports.save(report);actions.save(audit);return view(report,List.of(audit));}

    private ReportPage page(Page<ContentReport> source){List<ContentReport> content=source.getContent();Map<Long,List<ReportAction>> history=history(content);return new ReportPage(content.stream().map(r->view(r,history.getOrDefault(r.getId(),List.of()))).toList(),source.getNumber(),source.getSize(),source.hasNext());}
    private Map<Long,List<ReportAction>> history(List<ContentReport> content){if(content.isEmpty())return Map.of();return actions.findByReportIdInOrderByCreatedAtAsc(content.stream().map(ContentReport::getId).toList()).stream().collect(Collectors.groupingBy(ReportAction::getReportId));}
    private ReportAction audit(ContentReport report,long adminId,ActionState state,String note,String eventId){ReportAction audit=new ReportAction();audit.setReportId(report.getId());audit.setAdminId(adminId);audit.setResultStatus(report.getStatus());audit.setActionType(report.getDecisionAction());audit.setActionState(state);audit.setNote(note);audit.setEventId(eventId);audit.setCreatedAt(now());return audit;}
    private ReportView view(ContentReport r,List<ReportAction> history){return new ReportView(r.getId(),r.getReporterId(),r.getReporterName(),r.getTargetType(),r.getTargetId(),r.getReportedUserId(),r.getTargetSummary(),r.getReasonCode(),r.getDescription(),r.getStatus(),r.getDecisionAction(),r.getActionState(),r.getActionError(),r.getResolutionNote(),r.getCreatedAt(),r.getResolvedAt(),history.stream().map(a->new AuditView(a.getAdminId(),a.getResultStatus(),a.getActionType(),a.getActionState(),a.getNote(),a.getCreatedAt())).toList());}
    private void requireAdmin(CurrentActor actor){if(actor==null||!actor.admin())throw GovernanceException.forbidden("无管理员权限");accounts.requireActiveAdmin(actor.userId());}
    private GovernanceAction expected(ReportTargetType type){return switch(type){case ITEM->GovernanceAction.REMOVE_ITEM;case MESSAGE->GovernanceAction.REMOVE_MESSAGE;case USER->GovernanceAction.DISABLE_USER;};}
    private Pageable pageable(int page,int size){return PageRequest.of(Math.max(0,page),Math.min(50,Math.max(1,size)),Sort.by(Sort.Direction.DESC,"createdAt").and(Sort.by(Sort.Direction.DESC,"id")));}
    private String normalize(String value,int min,int max,String message){String text=value==null?"":value.trim();if(text.length()<min||text.length()>max)throw new IllegalArgumentException(message);return text;}private String trim(String value,int max){String text=value==null?"":value.trim();return text.length()<=max?text:text.substring(0,max);}private LocalDateTime now(){return LocalDateTime.now(clock);}
}
