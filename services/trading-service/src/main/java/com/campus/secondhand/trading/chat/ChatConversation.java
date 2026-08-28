package com.campus.secondhand.trading.chat;
import jakarta.persistence.*;import java.time.LocalDateTime;import java.util.UUID;
@Entity @Table(name="chat_conversations",uniqueConstraints={@UniqueConstraint(name="uq_chat_public_id",columnNames="public_id"),@UniqueConstraint(name="uq_chat_item_participants",columnNames={"item_id","buyer_id","seller_id"})})
public class ChatConversation{
 @Id @GeneratedValue(strategy=GenerationType.IDENTITY)private Long id;@Column(name="public_id",nullable=false,length=36)private String publicId=UUID.randomUUID().toString();
 @Column(name="item_id",nullable=false)private Long itemId;@Column(name="buyer_id",nullable=false)private Long buyerId;@Column(name="seller_id",nullable=false)private Long sellerId;
 @Column(name="buyer_nickname",nullable=false,length=80)private String buyerNickname;@Column(name="seller_nickname",nullable=false,length=80)private String sellerNickname;
 @Column(name="item_title_snapshot",nullable=false,length=120)private String itemTitleSnapshot;@Column(name="item_image_snapshot",length=255)private String itemImageSnapshot;
 @Column(name="last_message_preview",length=160)private String lastMessagePreview;@Column(name="last_message_at")private LocalDateTime lastMessageAt;
 @Column(name="next_sequence",nullable=false)private Long nextSequence=1L;@Column(name="buyer_last_read_sequence",nullable=false)private Long buyerLastReadSequence=0L;@Column(name="seller_last_read_sequence",nullable=false)private Long sellerLastReadSequence=0L;
 @Column(name="created_at",nullable=false,updatable=false)private LocalDateTime createdAt=LocalDateTime.now();@Version @Column(nullable=false)private Long version=0L;
 public Long getId(){return id;}public String getPublicId(){return publicId;}public Long getItemId(){return itemId;}public void setItemId(Long v){itemId=v;}public Long getBuyerId(){return buyerId;}public void setBuyerId(Long v){buyerId=v;}public Long getSellerId(){return sellerId;}public void setSellerId(Long v){sellerId=v;}
 public String getBuyerNickname(){return buyerNickname;}public void setBuyerNickname(String v){buyerNickname=v;}public String getSellerNickname(){return sellerNickname;}public void setSellerNickname(String v){sellerNickname=v;}
 public String getItemTitleSnapshot(){return itemTitleSnapshot;}public void setItemTitleSnapshot(String v){itemTitleSnapshot=v;}public String getItemImageSnapshot(){return itemImageSnapshot;}public void setItemImageSnapshot(String v){itemImageSnapshot=v;}
 public String getLastMessagePreview(){return lastMessagePreview;}public void setLastMessagePreview(String v){lastMessagePreview=v;}public LocalDateTime getLastMessageAt(){return lastMessageAt;}public void setLastMessageAt(LocalDateTime v){lastMessageAt=v;}
 public Long getNextSequence(){return nextSequence;}public void setNextSequence(Long v){nextSequence=v;}public Long getBuyerLastReadSequence(){return buyerLastReadSequence;}public void setBuyerLastReadSequence(Long v){buyerLastReadSequence=v;}public Long getSellerLastReadSequence(){return sellerLastReadSequence;}public void setSellerLastReadSequence(Long v){sellerLastReadSequence=v;}public LocalDateTime getCreatedAt(){return createdAt;}
}
