package com.campus.secondhand.item;

import com.campus.secondhand.order.OrderStatus;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.Set;

public interface ProductDetail {
    Optional<View> show(Long itemId, Long viewerId);

    enum Action {
        CHAT_SELLER,
        REQUEST_PURCHASE,
        VIEW_PURCHASE_REQUEST,
        MANAGE_LISTING,
        REPORT_ITEM
    }

    record View(Long id, String title, String category, BigDecimal price, String description,
                String imageUrl, String region, Set<String> tags, ItemStatus status,
                ItemModerationStatus moderationStatus, LocalDateTime createdAt, Long sellerId, Seller seller,
                Viewer viewer, List<RelatedItem> sellerItems) {
    }

    record Seller(Long id, String displayName, String region, Integer creditScore,
                  LocalDateTime lastActiveAt, long onSaleCount) {
    }

    record Viewer(boolean authenticated, boolean owner, List<Action> availableActions,
                  PurchaseRequest purchaseRequest) {
    }

    record PurchaseRequest(Long id, OrderStatus status, LocalDateTime expiresAt) {
    }

    record RelatedItem(Long id, String title, BigDecimal price, String imageUrl,
                       String region, Set<String> tags) {
    }
}
