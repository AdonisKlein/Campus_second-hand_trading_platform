package com.campus.secondhand.governance;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity @Table(name="outbox_events")
class GovernanceOutboxEvent {
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
    @Column(name="event_id",nullable=false,unique=true,length=80) private String eventId;
    @Column(name="event_type",nullable=false,length=120) private String eventType;
    @Column(nullable=false,columnDefinition="TEXT") private String payload;
    @Column(name="created_at",nullable=false) private LocalDateTime createdAt;
    @Column(name="published_at") private LocalDateTime publishedAt;
    protected GovernanceOutboxEvent() {}
    GovernanceOutboxEvent(String eventId,String eventType,String payload,LocalDateTime createdAt){this.eventId=eventId;this.eventType=eventType;this.payload=payload;this.createdAt=createdAt;}
    String payload(){return payload;} void markPublished(LocalDateTime at){publishedAt=at;}
}
