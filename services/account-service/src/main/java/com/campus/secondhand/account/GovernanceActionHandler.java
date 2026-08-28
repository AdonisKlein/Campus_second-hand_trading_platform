package com.campus.secondhand.account;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.*;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Entity
@Table(name="account_event_inbox")
class AccountInboxEvent {
 @Id @Column(name="event_id", length=80) String eventId;
 @Column(name="event_type", nullable=false, length=120) String eventType;
 @Column(name="processed_at", nullable=false) LocalDateTime processedAt;
 protected AccountInboxEvent() {}
 AccountInboxEvent(String id, String type) { eventId=id; eventType=type; processedAt=LocalDateTime.now(); }
}
interface AccountInboxRepository extends JpaRepository<AccountInboxEvent,String> {}

@Entity
@Table(name="account_event_outbox")
class AccountOutboxEvent {
 @Id @GeneratedValue(strategy=GenerationType.IDENTITY) Long id;
 @Column(name="event_id", nullable=false, unique=true, length=80) String eventId;
 @Column(name="event_type", nullable=false, length=120) String eventType;
 @Column(nullable=false, columnDefinition="TEXT") String payload;
 @Column(name="created_at", nullable=false) LocalDateTime createdAt;
 @Column(name="published_at") LocalDateTime publishedAt;
 protected AccountOutboxEvent() {}
 AccountOutboxEvent(String id, String type, String body) { eventId=id; eventType=type; payload=body; createdAt=LocalDateTime.now(); }
 void markPublished() { publishedAt=LocalDateTime.now(); }
 String getPayload() { return payload; }
}
interface AccountOutboxRepository extends JpaRepository<AccountOutboxEvent,Long> {
 java.util.List<AccountOutboxEvent> findByPublishedAtIsNullOrderById(Pageable page);
}

@Service
class GovernanceActionHandler {
 private final UserRepository users; private final AccountInboxRepository inbox; private final AccountOutboxRepository outbox;
 private final ObjectMapper mapper = new ObjectMapper();
 GovernanceActionHandler(UserRepository u, AccountInboxRepository i, AccountOutboxRepository o) { users=u; inbox=i; outbox=o; }

 boolean processed(String eventId) { return eventId != null && inbox.existsById(eventId); }

 @Transactional
 public void handle(String eventId, String type, JsonNode payload) {
  if (eventId == null || eventId.isBlank()) throw new IllegalArgumentException("eventId is required");
  if (inbox.existsById(eventId)) return;
  inbox.saveAndFlush(new AccountInboxEvent(eventId, type));
  String result = "GovernanceActionFailed", reason = null;
  Long targetId = payload == null ? null : payload.path("targetId").isIntegralNumber() ? payload.path("targetId").asLong() : null;
  String action = payload == null ? "" : payload.path("action").asText("");
  String targetType = payload == null ? "" : payload.path("targetType").asText("");
  if (!"GovernanceActionRequested".equals(type) || !"USER".equals(targetType)
      || !"DISABLE_USER".equals(action) || targetId == null || targetId < 1) {
   reason = "unsupported governance action";
  } else {
   User user = users.findLockedById(targetId).orElse(null);
   if (user == null) reason = "user not found";
   else if (!"STUDENT".equals(user.getRole())) reason = "only student accounts can be governed";
   else if ("DISABLED".equals(user.getStatus())) result = "GovernanceActionApplied";
   else { user.setStatus("DISABLED"); user.setAuthVersion(user.getAuthVersion() + 1); users.save(user); result = "GovernanceActionApplied"; }
  }
  Map<String,Object> body = new LinkedHashMap<>(); body.put("eventId", eventId + ":result"); body.put("correlationId", eventId);
  body.put("version", 1); body.put("occurredAt", LocalDateTime.now().toString()); body.put("producer", "account-service");
  body.put("type", result); body.put("reportId", payload == null ? null : payload.path("reportId").asLong()); body.put("targetId", targetId);
  if (reason != null) body.put("reason", reason);
  try { outbox.save(new AccountOutboxEvent(eventId + ":result", result, mapper.writeValueAsString(body))); }
  catch (Exception error) { throw new IllegalStateException("治理结果序列化失败", error); }
 }
}
