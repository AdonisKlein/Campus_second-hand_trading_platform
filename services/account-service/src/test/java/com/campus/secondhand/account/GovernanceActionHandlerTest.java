package com.campus.secondhand.account;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class GovernanceActionHandlerTest {
 private final UserRepository users = mock(UserRepository.class);
 private final AccountInboxRepository inbox = mock(AccountInboxRepository.class);
 private final AccountOutboxRepository outbox = mock(AccountOutboxRepository.class);
 private final GovernanceActionHandler handler = new GovernanceActionHandler(users, inbox, outbox);
 private final ObjectMapper json = new ObjectMapper();

 @Test
 void disablesStudentOnceAndEmitsAppliedResult() {
  User user = new User(); user.setRole("STUDENT"); user.setStatus("ACTIVE"); user.setAuthVersion(4);
  when(inbox.existsById("evt-1")).thenReturn(false); when(users.findLockedById(9L)).thenReturn(Optional.of(user));
  handler.handle("evt-1", "GovernanceActionRequested", json.createObjectNode().put("targetType", "USER").put("targetId", 9).put("action", "DISABLE_USER"));
  assertThat(user.getStatus()).isEqualTo("DISABLED"); assertThat(user.getAuthVersion()).isEqualTo(5);
  verify(outbox).save(argThat(event -> event.getPayload().contains("GovernanceActionApplied")));
 }

 @Test
 void refusesAdministratorWithoutChangingAccount() {
  User user = new User(); user.setRole("ADMIN"); user.setStatus("ACTIVE"); user.setAuthVersion(2);
  when(inbox.existsById("evt-2")).thenReturn(false); when(users.findLockedById(7L)).thenReturn(Optional.of(user));
  handler.handle("evt-2", "GovernanceActionRequested", json.createObjectNode().put("targetType", "USER").put("targetId", 7).put("action", "DISABLE_USER"));
  assertThat(user.getStatus()).isEqualTo("ACTIVE"); assertThat(user.getAuthVersion()).isEqualTo(2);
  verify(outbox).save(argThat(event -> event.getPayload().contains("GovernanceActionFailed")));
 }

 @Test
 void duplicateEventIsIgnoredBeforeAccountMutation() {
  when(inbox.existsById("evt-3")).thenReturn(true);
  handler.handle("evt-3", "GovernanceActionRequested", json.createObjectNode().put("targetType", "USER").put("targetId", 1).put("action", "DISABLE_USER"));
  verifyNoInteractions(users, outbox);
 }

 @Test
 void refusesMismatchedTargetTypeBeforeAccountMutation() {
  when(inbox.existsById("evt-4")).thenReturn(false);
  handler.handle("evt-4", "GovernanceActionRequested", json.createObjectNode().put("targetType", "ITEM").put("targetId", 9).put("action", "DISABLE_USER"));
  verifyNoInteractions(users);
  verify(outbox).save(argThat(event -> event.getPayload().contains("GovernanceActionFailed")));
 }
}
