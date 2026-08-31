package com.campus.secondhand.trading;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity @Table(name = "trade_orders")
public class TradeOrder {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(name="item_id",nullable=false) private Long itemId;
    @Column(name="buyer_id",nullable=false) private Long buyerId;
    @Column(name="seller_id",nullable=false) private Long sellerId;
    @Column(name="item_title",nullable=false,length=120) private String itemTitle;
    @Column(name="item_price",nullable=false,precision=10,scale=2) private BigDecimal itemPrice;
    @Column(name="item_image_url",length=255) private String itemImageUrl;
    @Column(name="buyer_nickname",nullable=false,length=80) private String buyerNickname;
    @Column(name="seller_nickname",nullable=false,length=80) private String sellerNickname;
    @Enumerated(EnumType.STRING) @Column(nullable=false,length=32) private OrderStatus status=OrderStatus.PURCHASE_REQUESTED;
    @Enumerated(EnumType.STRING) @Column(name="saga_state",nullable=false,length=32) private SagaState sagaState=SagaState.NONE;
    @Enumerated(EnumType.STRING) @Column(name="pending_final_status",length=32) private OrderStatus pendingFinalStatus;
    @Column(name="expires_at",nullable=false) private LocalDateTime expiresAt;
    @Column(name="closure_reason",length=160) private String closureReason;
    @Column(name="created_at",nullable=false,updatable=false) private LocalDateTime createdAt;
    @Column(name="updated_at",nullable=false) private LocalDateTime updatedAt;
    @Version @Column(nullable=false) private Long version=0L;
    @PrePersist void createTimes(){if(createdAt==null)createdAt=LocalDateTime.now();if(updatedAt==null)updatedAt=createdAt;}
    @PreUpdate void updateTime(){updatedAt=LocalDateTime.now();}
    public Long getId(){return id;} void setId(Long v){id=v;} public Long getItemId(){return itemId;} public void setItemId(Long v){itemId=v;}
    public Long getBuyerId(){return buyerId;} public void setBuyerId(Long v){buyerId=v;} public Long getSellerId(){return sellerId;} public void setSellerId(Long v){sellerId=v;}
    public String getItemTitle(){return itemTitle;} public void setItemTitle(String v){itemTitle=v;} public BigDecimal getItemPrice(){return itemPrice;} public void setItemPrice(BigDecimal v){itemPrice=v;}
    public String getItemImageUrl(){return itemImageUrl;} public void setItemImageUrl(String v){itemImageUrl=v;} public String getBuyerNickname(){return buyerNickname;} public void setBuyerNickname(String v){buyerNickname=v;}
    public String getSellerNickname(){return sellerNickname;} public void setSellerNickname(String v){sellerNickname=v;} public OrderStatus getStatus(){return status;} public void setStatus(OrderStatus v){status=v;}
    public SagaState getSagaState(){return sagaState;} public void setSagaState(SagaState v){sagaState=v;} public OrderStatus getPendingFinalStatus(){return pendingFinalStatus;} public void setPendingFinalStatus(OrderStatus v){pendingFinalStatus=v;}
    public LocalDateTime getExpiresAt(){return expiresAt;} public void setExpiresAt(LocalDateTime v){expiresAt=v;} public String getClosureReason(){return closureReason;} public void setClosureReason(String v){closureReason=v;}
    public LocalDateTime getCreatedAt(){return createdAt;} public void setCreatedAt(LocalDateTime v){createdAt=v;} public LocalDateTime getUpdatedAt(){return updatedAt;} public void setUpdatedAt(LocalDateTime v){updatedAt=v;}
}
