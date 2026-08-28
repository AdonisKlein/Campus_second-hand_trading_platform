package com.campus.secondhand.marketplace.message;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity @Table(name="messages")
public class Message {
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
    @Column(nullable=false) private Long itemId;
    @Column(nullable=false) private Long senderId;
    @Column(nullable=false) private Long receiverId;
    @Column(nullable=false,length=500) private String content;
    @Column(nullable=false) private LocalDateTime createdAt = LocalDateTime.now();
    public Long getId(){return id;} public Long getItemId(){return itemId;} public void setItemId(Long v){itemId=v;}
    public Long getSenderId(){return senderId;} public void setSenderId(Long v){senderId=v;}
    public Long getReceiverId(){return receiverId;} public void setReceiverId(Long v){receiverId=v;}
    public String getContent(){return content;} public void setContent(String v){content=v;}
    public LocalDateTime getCreatedAt(){return createdAt;}
}
