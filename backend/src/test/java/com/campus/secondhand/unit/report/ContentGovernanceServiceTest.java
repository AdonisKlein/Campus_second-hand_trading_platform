package com.campus.secondhand.unit.report;

import com.campus.secondhand.item.*;
import com.campus.secondhand.report.*;
import com.campus.secondhand.message.MessageRepository;
import com.campus.secondhand.user.User;
import com.campus.secondhand.user.UserRepository;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ContentGovernanceServiceTest {
    @Mock ContentReportRepository reports;
    @Mock ReportActionRepository actions;
    @Mock ItemRepository items;
    @Mock MessageRepository messages;
    @Mock UserRepository users;
    private ContentGovernanceService service;

    @BeforeEach void setUp() {
        service = new ContentGovernanceService(reports, actions, items, messages, users);
    }

    @Test void submitCreatesStudentReportWithNormalizedDescription() {
        when(users.findById(7L)).thenReturn(Optional.of(user(7L, "STUDENT", "ACTIVE")));
        when(reports.countByReporterIdAndCreatedAtAfter(any(), any())).thenReturn(0L);
        when(items.findById(20L)).thenReturn(Optional.of(item(20L, 8L)));
        when(reports.existsByReporterIdAndTargetTypeAndTargetId(7L, ReportTargetType.ITEM, 20L)).thenReturn(false);
        when(reports.saveAndFlush(any(ContentReport.class))).thenAnswer(invocation -> invocation.getArgument(0));
        var view = service.submit(7L, new ContentGovernance.ReportDraft(
            ReportTargetType.ITEM, 20L, ReportReason.FRAUD, "  商品信息与实际描述严重不符  "));
        assertAll(() -> assertEquals(7L, view.reporterId()), () -> assertEquals(20L, view.targetId()),
            () -> assertEquals("商品标题", view.targetSummary()),
            () -> assertEquals("商品信息与实际描述严重不符", view.description()),
            () -> assertEquals(ReportStatus.OPEN, view.status()));
    }

    @Test void submitRejectsRateLimitAndDuplicate() {
        when(users.findById(7L)).thenReturn(Optional.of(user(7L, "STUDENT", "ACTIVE")));
        when(reports.countByReporterIdAndCreatedAtAfter(any(), any())).thenReturn(20L);
        assertThrows(GovernanceRuleException.class, () -> service.submit(7L,
            new ContentGovernance.ReportDraft(ReportTargetType.ITEM, 20L, ReportReason.SPAM, "重复举报内容已经达到十个字")));
        when(reports.countByReporterIdAndCreatedAtAfter(any(), any())).thenReturn(0L);
        when(items.findById(20L)).thenReturn(Optional.of(item(20L, 8L)));
        when(reports.existsByReporterIdAndTargetTypeAndTargetId(7L, ReportTargetType.ITEM, 20L)).thenReturn(true);
        assertThrows(GovernanceRuleException.class, () -> service.submit(7L,
            new ContentGovernance.ReportDraft(ReportTargetType.ITEM, 20L, ReportReason.SPAM, "重复举报内容已经达到十个字")));
    }

    @Test void resolvedItemReportAppliesMatchingRemoveActionAndAudit() {
        when(users.findById(99L)).thenReturn(Optional.of(user(99L, "ADMIN", "ACTIVE")));
        when(reports.findLockedById(31L)).thenReturn(Optional.of(openReport(31L)));
        Item item = item(20L, 8L); when(items.findLockedById(20L)).thenReturn(Optional.of(item));
        when(users.findAllById(any())).thenReturn(java.util.List.of());
        var view = service.decide(99L, 31L, new ContentGovernance.Decision(
            ReportStatus.RESOLVED, GovernanceAction.REMOVE_ITEM, "确认商品违规"));
        assertAll(() -> assertEquals(ReportStatus.RESOLVED, view.status()),
            () -> assertEquals(GovernanceAction.REMOVE_ITEM, view.decisionAction()),
            () -> assertEquals(ItemModerationStatus.REMOVED, item.getModerationStatus()));
        verify(actions).save(any(ReportAction.class));
    }

    @Test void mismatchedActionFailsAndDismissalDoesNotMutateContent() {
        when(users.findById(99L)).thenReturn(Optional.of(user(99L, "ADMIN", "ACTIVE")));
        when(reports.findLockedById(31L)).thenReturn(Optional.of(openReport(31L)));
        assertThrows(GovernanceRuleException.class, () -> service.decide(99L, 31L,
            new ContentGovernance.Decision(ReportStatus.RESOLVED, GovernanceAction.DISABLE_USER, "措施错误")));
        when(reports.findLockedById(32L)).thenReturn(Optional.of(openReport(32L)));
        var dismissed = service.decide(99L, 32L,
            new ContentGovernance.Decision(ReportStatus.DISMISSED, GovernanceAction.NONE, "证据不足"));
        assertEquals(ReportStatus.DISMISSED, dismissed.status());
        verify(items, never()).findLockedById(any());
    }

    private User user(Long id, String role, String status) { User value = new User(); value.setId(id);
        value.setUsername("u" + id); value.setNickname("用户" + id); value.setEmail("u" + id + "@example.com");
        value.setRole(role); value.setStatus(status); return value; }
    private Item item(Long id, Long sellerId) { Item value = new Item(); value.setId(id); value.setSellerId(sellerId);
        value.setTitle("商品标题"); value.setModerationStatus(ItemModerationStatus.VISIBLE); return value; }
    private ContentReport openReport(Long id) { ContentReport value = new ContentReport(); value.setTargetType(ReportTargetType.ITEM);
        value.setTargetId(20L); value.setReporterId(7L); value.setReportedUserId(8L); value.setTargetSummary("商品标题");
        value.setReasonCode(ReportReason.FRAUD); value.setDescription("举报说明");
        try { var field = ContentReport.class.getDeclaredField("id"); field.setAccessible(true); field.set(value, id); }
        catch (ReflectiveOperationException e) { throw new AssertionError(e); } return value; }
}
