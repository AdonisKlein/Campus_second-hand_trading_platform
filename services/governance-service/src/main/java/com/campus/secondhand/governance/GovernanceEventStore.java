package com.campus.secondhand.governance;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.*;
import java.util.*;
import org.springframework.stereotype.Component;

@Component
class GovernanceEventStore {
    private final GovernanceOutboxRepository outbox; private final Clock clock; private final ObjectMapper mapper=new ObjectMapper();
    GovernanceEventStore(GovernanceOutboxRepository outbox,Clock clock){this.outbox=outbox;this.clock=clock;}
    String request(ContentReport report,long adminId){String eventId=UUID.randomUUID().toString();Map<String,Object> event=new LinkedHashMap<>();
        event.put("eventId",eventId);event.put("correlationId",CorrelationIdFilter.current());event.put("version",1);event.put("occurredAt",LocalDateTime.now(clock).toString());event.put("producer","governance-service");event.put("type","GovernanceActionRequested");event.put("reportId",report.getId());event.put("targetType",report.getTargetType().name());event.put("targetId",report.getTargetId());event.put("action",report.getDecisionAction().name());event.put("adminId",adminId);
        try{outbox.save(new GovernanceOutboxEvent(eventId,"GovernanceActionRequested",mapper.writeValueAsString(event),LocalDateTime.now(clock)));return eventId;}catch(Exception error){throw new IllegalStateException("治理事件序列化失败",error);}}
}
