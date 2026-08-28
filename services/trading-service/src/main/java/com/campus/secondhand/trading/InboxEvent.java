package com.campus.secondhand.trading;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity @Table(name="inbox_events")
class InboxEvent {
    @Id @Column(name="event_id",length=80) private String eventId;
    @Column(name="event_type",nullable=false,length=120) private String eventType;
    @Column(name="processed_at",nullable=false) private LocalDateTime processedAt;
    protected InboxEvent() {}
    InboxEvent(String id,String type,LocalDateTime at){eventId=id;eventType=type;processedAt=at;}
}
