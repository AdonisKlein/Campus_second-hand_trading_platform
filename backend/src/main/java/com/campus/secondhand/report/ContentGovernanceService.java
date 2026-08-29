package com.campus.secondhand.report;

import com.campus.secondhand.item.*;
import com.campus.secondhand.message.*;
import com.campus.secondhand.user.*;
import com.campus.secondhand.chat.*;
import java.time.LocalDateTime;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ContentGovernanceService implements ContentGovernance {
    private final ContentReportRepository reports;
    private final ReportActionRepository actions;
    private final ItemRepository items;
    private final MessageRepository messages;
    private final UserRepository users;
    private final ChatConversationRepository conversations;
    private final ChatMessageRepository chatMessages;

    public ContentGovernanceService(ContentReportRepository reports, ReportActionRepository actions,
                                    ItemRepository items, MessageRepository messages, UserRepository users) {
        this(reports, actions, items, messages, users, null, null);
    }

    @Autowired
    public ContentGovernanceService(ContentReportRepository reports, ReportActionRepository actions,
                                    ItemRepository items, MessageRepository messages, UserRepository users,
                                    ChatConversationRepository conversations, ChatMessageRepository chatMessages) {
        this.reports = reports; this.actions = actions; this.items = items; this.messages = messages; this.users = users;
        this.conversations = conversations; this.chatMessages = chatMessages;
    }

    @Override @Transactional
    public ReportView submit(Long reporterId, ReportDraft draft) {
        requireStudent(reporterId);
        if (reports.countByReporterIdAndCreatedAtAfter(reporterId, LocalDateTime.now().minusHours(24)) >= 20) {
            throw new GovernanceRuleException("24 小时内最多提交 20 条举报，请稍后再试");
        }
        if (draft == null || draft.targetType() == null || draft.targetId() == null || draft.targetId() < 1
            || draft.reasonCode() == null) throw new GovernanceRuleException("举报信息不完整");
        String description = normalizeText(draft.description(), 10, 1000, "请用 10—1000 个字说明举报原因");
        TargetSnapshot target = snapshot(draft.targetType(), draft.targetId());
        if (Objects.equals(reporterId, target.reportedUserId())) throw new GovernanceRuleException("不能举报自己发布的内容");
        if (reports.existsByReporterIdAndTargetTypeAndTargetId(reporterId, draft.targetType(), draft.targetId())) {
            throw new GovernanceRuleException("你已经举报过该内容，可在“我的举报”查看进度");
        }
        ContentReport report = new ContentReport();
        report.setReporterId(reporterId); report.setTargetType(draft.targetType()); report.setTargetId(draft.targetId());
        report.setReportedUserId(target.reportedUserId()); report.setTargetSummary(target.summary());
        ChatEvidence evidence = chatEvidence(reporterId, target.reportedUserId(), draft.contextConversationId());
        report.setContextConversationId(evidence.conversationId()); report.setEvidenceSnapshot(evidence.snapshot());
        report.setReasonCode(draft.reasonCode()); report.setDescription(description);
        return view(reports.saveAndFlush(report), Map.of(), List.of());
    }

    @Override @Transactional(readOnly = true)
    public ReportPage listMine(Long reporterId, int page, int size) {
        requireStudent(reporterId);
        return page(reports.findByReporterIdOrderByCreatedAtDesc(reporterId, pageable(page, size)));
    }

    @Override @Transactional(readOnly = true)
    public ReceivedReportPage listReceived(Long reportedUserId, int page, int size) {
        requireStudent(reportedUserId);
        var result = reports.findByReportedUserIdAndStatusNotOrderByCreatedAtDesc(
            reportedUserId, ReportStatus.OPEN, pageable(page, size));
        return new ReceivedReportPage(result.getContent().stream().map(report -> new ReceivedReportView(
            report.getId(), report.getTargetType(), report.getTargetSummary(), report.getReasonCode(), report.getStatus(),
            report.getDecisionAction(), report.getResolutionNote(), report.getCreatedAt(), report.getResolvedAt())).toList(),
            result.getNumber(), result.getSize(), result.hasNext());
    }

    @Override @Transactional(readOnly = true)
    public ReportPage listForAdmin(ReportStatus status, int page, int size) {
        var result = status == null ? reports.findAll(pageable(page, size))
            : reports.findByStatusOrderByCreatedAtDesc(status, pageable(page, size));
        return page(result);
    }

    @Override @Transactional
    public ReportView decide(Long adminId, Long reportId, Decision decision) {
        User admin = users.findById(adminId).orElseThrow(() -> new GovernanceRuleException("管理员不存在"));
        if (!"ADMIN".equals(admin.getRole()) || !"ACTIVE".equals(admin.getStatus())) throw new GovernanceRuleException("无管理员权限");
        ContentReport report = reports.findLockedById(reportId).orElseThrow(() -> new GovernanceRuleException("举报不存在"));
        if (report.getStatus() != ReportStatus.OPEN) throw new GovernanceRuleException("该举报已经处理，不能重复操作");
        if (decision == null || (decision.status() != ReportStatus.RESOLVED && decision.status() != ReportStatus.DISMISSED)) {
            throw new GovernanceRuleException("处理结果不合法");
        }
        String note = normalizeText(decision.note(), 2, 1000, "请填写处理说明");
        GovernanceAction action = decision.status() == ReportStatus.DISMISSED ? GovernanceAction.NONE : decision.action();
        if (decision.status() == ReportStatus.RESOLVED) applyAction(report, action, note);
        else if (decision.action() != null && decision.action() != GovernanceAction.NONE) throw new GovernanceRuleException("驳回举报不能执行治理措施");
        report.setStatus(decision.status()); report.setDecisionAction(action); report.setResolutionNote(note);
        report.setHandledBy(adminId); report.setResolvedAt(LocalDateTime.now()); report.setUpdatedAt(LocalDateTime.now());
        ReportAction audit = new ReportAction(); audit.setReportId(report.getId()); audit.setAdminId(adminId);
        audit.setResultStatus(decision.status()); audit.setActionType(action); audit.setNote(note);
        reports.save(report); actions.save(audit);
        return view(report, userMap(List.of(report)), List.of(audit));
    }

    private void applyAction(ContentReport report, GovernanceAction action, String note) {
        GovernanceAction expected = switch (report.getTargetType()) {
            case ITEM -> GovernanceAction.REMOVE_ITEM;
            case MESSAGE -> GovernanceAction.REMOVE_MESSAGE;
            case USER -> GovernanceAction.DISABLE_USER;
        };
        if (action != expected) throw new GovernanceRuleException("治理措施与举报对象不匹配");
        switch (action) {
            case REMOVE_ITEM -> {
                Item item = items.findLockedById(report.getTargetId()).orElseThrow(() -> new GovernanceRuleException("商品已经不存在"));
                item.setModerationStatus(ItemModerationStatus.REMOVED); items.save(item);
            }
            case REMOVE_MESSAGE -> {
                Message message = messages.findById(report.getTargetId()).orElseThrow(() -> new GovernanceRuleException("留言已经不存在"));
                messages.delete(message);
            }
            case DISABLE_USER -> {
                User user = users.findById(report.getTargetId()).orElseThrow(() -> new GovernanceRuleException("用户已经不存在"));
                if ("ADMIN".equals(user.getRole())) throw new GovernanceRuleException("不能通过举报禁用管理员");
                user.setStatus("DISABLED"); user.setStatusReason(note);
                user.setAuthVersion(user.getAuthVersion() + 1); users.save(user);
            }
            default -> throw new GovernanceRuleException("请选择治理措施");
        }
    }

    private TargetSnapshot snapshot(ReportTargetType type, Long id) {
        return switch (type) {
            case ITEM -> {
                Item item = items.findById(id).filter(value -> value.getModerationStatus() == ItemModerationStatus.VISIBLE)
                    .orElseThrow(() -> new GovernanceRuleException("无法举报该内容"));
                yield new TargetSnapshot(item.getSellerId(), trimSummary(item.getTitle()));
            }
            case MESSAGE -> {
                Message message = messages.findById(id).orElseThrow(() -> new GovernanceRuleException("无法举报该内容"));
                yield new TargetSnapshot(message.getSenderId(), trimSummary(message.getContent()));
            }
            case USER -> {
                User user = users.findById(id).filter(value -> "STUDENT".equals(value.getRole()) && "ACTIVE".equals(value.getStatus()))
                    .orElseThrow(() -> new GovernanceRuleException("无法举报该内容"));
                yield new TargetSnapshot(user.getId(), trimSummary(displayName(user)));
            }
        };
    }

    private ReportPage page(org.springframework.data.domain.Page<ContentReport> result) {
        List<ContentReport> content = result.getContent();
        Map<Long, User> userMap = userMap(content);
        Map<Long, List<ReportAction>> history = content.isEmpty() ? Map.of() : actions
            .findByReportIdInOrderByCreatedAtAsc(content.stream().map(ContentReport::getId).toList()).stream()
            .collect(Collectors.groupingBy(ReportAction::getReportId));
        return new ReportPage(content.stream().map(r -> view(r, userMap, history.getOrDefault(r.getId(), List.of()))).toList(),
            result.getNumber(), result.getSize(), result.hasNext());
    }

    private Map<Long, User> userMap(List<ContentReport> content) {
        return users.findAllById(content.stream().map(ContentReport::getReporterId).distinct().toList()).stream()
            .collect(Collectors.toMap(User::getId, Function.identity()));
    }

    private ReportView view(ContentReport report, Map<Long, User> userMap, List<ReportAction> history) {
        User reporter = userMap.get(report.getReporterId());
        return new ReportView(report.getId(), report.getReporterId(), reporter == null ? "学生用户" : displayName(reporter),
            report.getTargetType(), report.getTargetId(), report.getReportedUserId(), report.getTargetSummary(),
            report.getReasonCode(), report.getDescription(), report.getEvidenceSnapshot(), report.getStatus(), report.getDecisionAction(),
            report.getResolutionNote(), report.getCreatedAt(), report.getResolvedAt(), history.stream().map(a ->
                new AuditView(a.getAdminId(), a.getResultStatus(), a.getActionType(), a.getNote(), a.getCreatedAt())).toList());
    }

    private void requireStudent(Long id) {
        User user = users.findById(id).orElseThrow(() -> new GovernanceRuleException("用户不存在"));
        if (!"STUDENT".equals(user.getRole()) || !"ACTIVE".equals(user.getStatus())) throw new GovernanceRuleException("只有学生用户可以提交举报");
    }
    private PageRequest pageable(int page, int size) { return PageRequest.of(Math.max(0, page), Math.min(50, Math.max(1, size)), Sort.by(Sort.Direction.DESC, "createdAt")); }
    private String normalizeText(String value, int min, int max, String message) { String text = value == null ? "" : value.trim(); if (text.length() < min || text.length() > max) throw new GovernanceRuleException(message); return text; }
    private String trimSummary(String value) { String text = value == null ? "" : value.trim(); return text.length() <= 500 ? text : text.substring(0, 500); }
    private String displayName(User user) { return user.getNickname() == null || user.getNickname().isBlank() ? user.getUsername() : user.getNickname(); }
    private ChatEvidence chatEvidence(Long reporterId, Long reportedUserId, String publicId) {
        if (publicId == null || publicId.isBlank()) return new ChatEvidence(null, null);
        if (conversations == null || chatMessages == null) {
            throw new GovernanceRuleException("举报聊天证据服务不可用");
        }
        ChatConversation conversation = conversations.findByPublicId(publicId)
            .orElseThrow(() -> new GovernanceRuleException("无法核验举报所关联的私聊会话"));
        boolean reporterIsBuyer = reporterId.equals(conversation.getBuyerId());
        boolean reporterIsSeller = reporterId.equals(conversation.getSellerId());
        Long other = reporterIsBuyer ? conversation.getSellerId() : reporterIsSeller ? conversation.getBuyerId() : null;
        if (other == null || !other.equals(reportedUserId)) {
            throw new GovernanceRuleException("举报人与被举报人不属于该私聊会话");
        }
        List<ChatMessage> recent = chatMessages.findPage(conversation.getId(), Long.MAX_VALUE, PageRequest.of(0, 30));
        Collections.reverse(recent);
        String snapshot = recent.stream().map(message -> {
            String speaker = message.getSenderId().equals(reporterId) ? "举报人" : "被举报人";
            return "[%s] %s：%s".formatted(message.getCreatedAt(), speaker, message.getBody());
        }).collect(Collectors.joining("\n"));
        if (snapshot.isBlank()) snapshot = "该会话尚无消息";
        return new ChatEvidence(conversation.getId(), snapshot);
    }
    private record TargetSnapshot(Long reportedUserId, String summary) {}
    private record ChatEvidence(Long conversationId, String snapshot) {}
}
