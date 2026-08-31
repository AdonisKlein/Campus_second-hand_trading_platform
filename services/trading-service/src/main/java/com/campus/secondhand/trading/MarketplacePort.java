package com.campus.secondhand.trading;

import java.math.BigDecimal;
import java.util.Optional;

public interface MarketplacePort {
    Optional<ItemSnapshot> find(long itemId);

    default ItemSnapshot require(long itemId) {
        return find(itemId).orElseThrow(() -> TradingException.notFound("商品不存在"));
    }

    record ItemSnapshot(long id, long sellerId, String title, BigDecimal price, String imageUrl,
                        String status, String moderationStatus) {
        public boolean publiclyTradable() {
            return "ON_SALE".equals(status) && "VISIBLE".equals(moderationStatus);
        }
    }
}
