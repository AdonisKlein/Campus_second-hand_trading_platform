package com.campus.secondhand.order;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

public record OrderView(Long id, Long itemId, String itemTitle, BigDecimal itemPrice,
                        Long buyerId, String buyerNickname, Long sellerId, String sellerNickname,
                        OrderStatus status, LocalDateTime expiresAt, String closureReason,
                        LocalDateTime createdAt, List<OrderAction> allowedActions) {}
