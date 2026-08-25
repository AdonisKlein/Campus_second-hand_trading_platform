package com.campus.secondhand.order;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "trade_orders")
public class TradeOrder {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(nullable = false) private Long itemId;
    @Column(nullable = false) private Long buyerId;
    @Column(nullable = false) private Long sellerId;
    @Column(nullable = false, length = 120) private String itemTitle;
    @Column(nullable = false, precision = 10, scale = 2) private BigDecimal itemPrice;
    @Column(nullable = false, length = 80) private String buyerNickname;
    @Column(nullable = false, length = 80) private String sellerNickname;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private OrderStatus status = OrderStatus.PURCHASE_REQUESTED;
    @Column(name = "expires_at", nullable = false) private LocalDateTime expiresAt;
    @Column(name = "closure_reason", length = 160) private String closureReason;
    @Column(nullable = false, updatable = false) private LocalDateTime createdAt = LocalDateTime.now();
    @Column(nullable = false) private LocalDateTime updatedAt = LocalDateTime.now();
    @Version private Long version;

    @PreUpdate void touch() { updatedAt = LocalDateTime.now(); }

    public Long getId() { return id; }
    public Long getItemId() { return itemId; }
    public void setItemId(Long itemId) { this.itemId = itemId; }
    public Long getBuyerId() { return buyerId; }
    public void setBuyerId(Long buyerId) { this.buyerId = buyerId; }
    public Long getSellerId() { return sellerId; }
    public void setSellerId(Long sellerId) { this.sellerId = sellerId; }
    public String getItemTitle() { return itemTitle; }
    public void setItemTitle(String itemTitle) { this.itemTitle = itemTitle; }
    public BigDecimal getItemPrice() { return itemPrice; }
    public void setItemPrice(BigDecimal itemPrice) { this.itemPrice = itemPrice; }
    public String getBuyerNickname() { return buyerNickname; }
    public void setBuyerNickname(String buyerNickname) { this.buyerNickname = buyerNickname; }
    public String getSellerNickname() { return sellerNickname; }
    public void setSellerNickname(String sellerNickname) { this.sellerNickname = sellerNickname; }
    public OrderStatus getStatus() { return status; }
    public void setStatus(OrderStatus status) { this.status = status; }
    public LocalDateTime getExpiresAt() { return expiresAt; }
    public void setExpiresAt(LocalDateTime value) { this.expiresAt = value; }
    public String getClosureReason() { return closureReason; }
    public void setClosureReason(String value) { this.closureReason = value; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
}
