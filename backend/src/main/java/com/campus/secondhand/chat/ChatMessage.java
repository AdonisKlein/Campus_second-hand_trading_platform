package com.campus.secondhand.chat;
import jakarta.persistence.*;
import java.time.LocalDateTime;
@Entity @Table(name="chat_messages", uniqueConstraints=@UniqueConstraint(name="uq_chat_message_sequence", columnNames={"conversation_id","sequence_number"}))
public class ChatMessage {
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
    @Column(name="conversation_id",nullable=false) private Long conversationId;
    @Column(name="sender_id",nullable=false) private Long senderId;
    @Column(name="sequence_number",nullable=false) private Long sequenceNumber;
    @Column(nullable=false,length=2000) private String body;
    @Column(name="created_at",nullable=false,updatable=false) private LocalDateTime createdAt=LocalDateTime.now();
    public Long getId(){return id;} public Long getConversationId(){return conversationId;} public void setConversationId(Long v){conversationId=v;}
    public Long getSenderId(){return senderId;} public void setSenderId(Long v){senderId=v;} public Long getSequenceNumber(){return sequenceNumber;}
    public void setSequenceNumber(Long v){sequenceNumber=v;} public String getBody(){return body;} public void setBody(String v){body=v;} public LocalDateTime getCreatedAt(){return createdAt;}
}
