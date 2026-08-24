package com.campus.secondhand.item;

import java.util.List;
import java.util.LinkedHashSet;
import java.util.Set;
import com.campus.secondhand.user.UserRepository;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class SellerInventoryService implements SellerInventory {
    private final ItemRepository items;
    private final UserRepository users;

    public SellerInventoryService(ItemRepository items, UserRepository users) {
        this.items = items;
        this.users = users;
    }

    @Override
    @Transactional
    public SellerItemView publish(Long sellerId, ItemDraft draft) {
        requireActiveSeller(sellerId);
        Item item = new Item();
        item.setSellerId(sellerId);
        apply(item, draft);
        return view(items.save(item));
    }

    @Override
    @Transactional(readOnly = true)
    public List<SellerItemView> list(Long sellerId) {
        return items.findBySellerIdOrderByCreatedAtDesc(sellerId).stream().map(this::view).toList();
    }

    @Override
    @Transactional
    public SellerItemView revise(Long sellerId, Long itemId, ItemDraft draft) {
        Item item = ownedLocked(sellerId, itemId);
        if (!editable(item)) {
            throw new SellerInventoryRuleException("交易中或已售出的商品不能修改");
        }
        apply(item, draft);
        return view(items.save(item));
    }

    @Override
    @Transactional
    public SellerItemView act(Long sellerId, Long itemId, SellerItemAction action) {
        Item item = ownedLocked(sellerId, itemId);
        switch (action) {
            case WITHDRAW -> {
                if (item.getStatus() != ItemStatus.ON_SALE) {
                    throw new SellerInventoryRuleException("只有正在出售的商品可以下架");
                }
                item.setStatus(ItemStatus.WITHDRAWN);
            }
            case RELIST -> {
                if (item.getStatus() != ItemStatus.WITHDRAWN) {
                    throw new SellerInventoryRuleException("只有卖家已下架的商品可以重新上架");
                }
                if (item.getModerationStatus() != ItemModerationStatus.VISIBLE) {
                    throw new SellerInventoryRuleException("管理员下架的商品不能由卖家重新上架");
                }
                item.setStatus(ItemStatus.ON_SALE);
            }
        }
        return view(items.save(item));
    }

    private Item ownedLocked(Long sellerId, Long itemId) {
        Item item = items.findLockedById(itemId)
            .orElseThrow(() -> new SellerInventoryRuleException("商品不存在"));
        if (!sellerId.equals(item.getSellerId())) {
            throw new AccessDeniedException("不能操作其他用户发布的商品");
        }
        return item;
    }

    private void requireActiveSeller(Long sellerId) {
        boolean active = users.findById(sellerId)
            .map(user -> "ACTIVE".equals(user.getStatus()) && "STUDENT".equals(user.getRole()))
            .orElse(false);
        if (!active) {
            throw new AccessDeniedException("只有正常状态的用户可以发布商品");
        }
    }

    private void apply(Item item, ItemDraft draft) {
        item.setTitle(draft.title().trim());
        item.setCategory(draft.category().trim());
        item.setPrice(draft.price());
        item.setDescription(normalizeOptional(draft.description()));
        String imageUrl = normalizeOptional(draft.imageUrl());
        String ownedImagePattern = "^/media/product-images/" + item.getSellerId()
            + "/[0-9a-fA-F-]{36}\\.(jpg|png)$";
        if (imageUrl != null && !imageUrl.matches(ownedImagePattern)) {
            throw new SellerInventoryRuleException("只能使用本人上传的商品图片");
        }
        item.setImageUrl(imageUrl);
        item.setRegion(MarketplaceOptions.normalizeRegion(draft.region()));
        item.setTags(new LinkedHashSet<>(MarketplaceOptions.normalizeTags(draft.tags())));
    }

    private String normalizeOptional(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private boolean editable(Item item) {
        return item.getStatus() == ItemStatus.ON_SALE || item.getStatus() == ItemStatus.WITHDRAWN;
    }

    private SellerItemView view(Item item) {
        List<SellerItemAction> actions;
        if (item.getStatus() == ItemStatus.ON_SALE) {
            actions = List.of(SellerItemAction.WITHDRAW);
        } else if (item.getStatus() == ItemStatus.WITHDRAWN
            && item.getModerationStatus() == ItemModerationStatus.VISIBLE) {
            actions = List.of(SellerItemAction.RELIST);
        } else {
            actions = List.of();
        }
        return new SellerItemView(item.getId(), item.getTitle(), item.getCategory(), item.getPrice(),
            item.getDescription(), item.getImageUrl(), item.getRegion(), Set.copyOf(item.getTags()),
            item.getSellerId(), item.getStatus(),
            item.getModerationStatus(), item.getCreatedAt(), editable(item), actions);
    }
}
