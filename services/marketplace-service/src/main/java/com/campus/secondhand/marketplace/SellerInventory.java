package com.campus.secondhand.marketplace;

import java.math.BigDecimal;
import java.util.List;
import java.util.Set;

public interface SellerInventory {
    SellerItemView publish(Long sellerId, ItemDraft draft);
    List<SellerItemView> list(Long sellerId);
    SellerItemView revise(Long sellerId, Long itemId, ItemDraft draft);
    SellerItemView act(Long sellerId, Long itemId, SellerItemAction action);

    record ItemDraft(String title, String category, BigDecimal price, String description,
                     String imageUrl, String region, Set<String> tags) { }
}
