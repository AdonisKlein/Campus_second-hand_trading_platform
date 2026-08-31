package com.campus.secondhand.marketplace;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class SellerInventoryService implements SellerInventory {
    private final ItemRepository items;
    private final AccountPublicPort accounts;

    public SellerInventoryService(ItemRepository items, AccountPublicPort accounts) {
        this.items = items;
        this.accounts = accounts;
    }

    @Override @Transactional
    public SellerItemView publish(Long sellerId, ItemDraft draft) {
        requireActiveStudent(sellerId);
        Item item = new Item();
        item.setSellerId(sellerId);
        apply(item, draft);
        return view(items.save(item));
    }

    @Override @Transactional(readOnly = true)
    public List<SellerItemView> list(Long sellerId) {
        return items.findBySellerIdOrderByCreatedAtDesc(sellerId).stream().map(this::view).toList();
    }

    @Override @Transactional
    public SellerItemView revise(Long sellerId, Long itemId, ItemDraft draft) {
        Item item = owned(sellerId, itemId);
        if (!editable(item)) throw new MarketplaceException("ITEM_NOT_EDITABLE", "交易中或已售出的商品不能修改");
        apply(item, draft);
        return view(item);
    }

    @Override @Transactional
    public SellerItemView act(Long sellerId, Long itemId, SellerItemAction action) {
        Item item = owned(sellerId, itemId);
        if (action == SellerItemAction.WITHDRAW && item.getStatus() == ItemStatus.ON_SALE) {
            item.setStatus(ItemStatus.WITHDRAWN);
        } else if (action == SellerItemAction.RELIST && item.getStatus() == ItemStatus.WITHDRAWN
                && item.getModerationStatus() == ItemModerationStatus.VISIBLE) {
            item.setStatus(ItemStatus.ON_SALE);
        } else {
            throw new MarketplaceException("ILLEGAL_ITEM_ACTION", "当前商品状态不能执行该操作");
        }
        return view(item);
    }

    private Item owned(Long sellerId, Long itemId) {
        Item item = items.findLockedById(itemId)
                .orElseThrow(() -> new MarketplaceException("ITEM_NOT_FOUND", "商品不存在"));
        if (!sellerId.equals(item.getSellerId())) throw new AccessDeniedException("不能操作其他用户发布的商品");
        return item;
    }

    private void requireActiveStudent(Long sellerId) {
        var account = accounts.findPublic(sellerId)
                .orElseThrow(() -> new AccessDeniedException("账号当前不能发布商品"));
        if (!"ACTIVE".equals(account.status()) || !"STUDENT".equals(account.role())) {
            throw new AccessDeniedException("只有正常状态的学生账号可以发布商品");
        }
    }

    private void apply(Item item, ItemDraft draft) {
        item.setTitle(draft.title().trim());
        item.setCategory(draft.category().trim());
        item.setPrice(draft.price());
        item.setDescription(optional(draft.description()));
        String image = optional(draft.imageUrl());
        if (image != null && !image.matches("^/media/product-images/" + item.getSellerId()
                + "/[0-9a-fA-F-]{36}\\.(jpg|png)$")) {
            throw new MarketplaceException("INVALID_IMAGE_OWNER", "只能使用本人上传的商品图片");
        }
        item.setImageUrl(image);
        item.setRegion(MarketplaceOptions.normalizeRegion(draft.region()));
        item.setTags(new LinkedHashSet<>(MarketplaceOptions.normalizeTags(draft.tags())));
    }

    private String optional(String value) { return value == null || value.isBlank() ? null : value.trim(); }
    private boolean editable(Item item) { return item.getStatus() == ItemStatus.ON_SALE || item.getStatus() == ItemStatus.WITHDRAWN; }
    private SellerItemView view(Item item) {
        List<SellerItemAction> actions = item.getStatus() == ItemStatus.ON_SALE
                ? List.of(SellerItemAction.WITHDRAW)
                : item.getStatus() == ItemStatus.WITHDRAWN && item.getModerationStatus() == ItemModerationStatus.VISIBLE
                    ? List.of(SellerItemAction.RELIST) : List.of();
        return new SellerItemView(item.getId(), item.getTitle(), item.getCategory(), item.getPrice(),
                item.getDescription(), item.getImageUrl(), item.getRegion(), Set.copyOf(item.getTags()),
                item.getSellerId(), item.getStatus(), item.getModerationStatus(), item.getCreatedAt(),
                editable(item), actions);
    }
}
