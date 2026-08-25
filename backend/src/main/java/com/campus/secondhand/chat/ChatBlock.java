package com.campus.secondhand.chat;
import jakarta.persistence.*;
import java.time.LocalDateTime;
@Entity @Table(name="chat_blocks", uniqueConstraints=@UniqueConstraint(name="uq_chat_block",columnNames={"blocker_id","blocked_id"}))
public class ChatBlock {
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
    @Column(name="blocker_id",nullable=false) private Long blockerId;
    @Column(name="blocked_id",nullable=false) private Long blockedId;
    @Column(name="created_at",nullable=false,updatable=false) private LocalDateTime createdAt=LocalDateTime.now();
    public Long getId(){return id;} public Long getBlockerId(){return blockerId;} public void setBlockerId(Long v){blockerId=v;}
    public Long getBlockedId(){return blockedId;} public void setBlockedId(Long v){blockedId=v;} public LocalDateTime getCreatedAt(){return createdAt;}
}
