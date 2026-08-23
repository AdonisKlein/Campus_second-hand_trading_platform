package com.campus.secondhand.item;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

public record SellerItemView(
    Long id,
    String title,
    String category,
    BigDecimal price,
    String description,
    String imageUrl,
    Long sellerId,
    ItemStatus status,
    ItemModerationStatus moderationStatus,
    LocalDateTime createdAt,
    boolean editable,
    List<SellerItemAction> allowedActions
) {
}
