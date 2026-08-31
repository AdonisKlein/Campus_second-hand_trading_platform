package com.campus.secondhand.governance;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity @Table(name="inbox_events")
class GovernanceInboxEvent {
    @Id @Column(name="event_id",length=80) private String eventId;
    @Column(name="event_type",nullable=false,length=120) private String eventType;
    @Column(name="processed_at",nullable=false) private LocalDateTime processedAt;
    protected GovernanceInboxEvent() {}
    GovernanceInboxEvent(String eventId,String eventType,LocalDateTime processedAt){this.eventId=eventId;this.eventType=eventType;this.processedAt=processedAt;}
}
