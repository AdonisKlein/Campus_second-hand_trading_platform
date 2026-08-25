package com.campus.secondhand.item;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

public record SellerItemView(
    Long id,
    String title,
    String category,
    BigDecimal price,
    String description,
    String imageUrl,
    String region,
    Set<String> tags,
    Long sellerId,
    ItemStatus status,
    ItemModerationStatus moderationStatus,
    LocalDateTime createdAt,
    boolean editable,
    List<SellerItemAction> allowedActions
) {
}
