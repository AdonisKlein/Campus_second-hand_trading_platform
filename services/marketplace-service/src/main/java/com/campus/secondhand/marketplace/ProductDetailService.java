package com.campus.secondhand.marketplace;

import java.time.LocalDateTime;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class ProductDetailService implements ProductDetail {
    private final ItemRepository items;
    private final AccountPublicPort accounts;
    private final TradingInquiryPort trading;

    public ProductDetailService(ItemRepository items, AccountPublicPort accounts, TradingInquiryPort trading) {
        this.items = items; this.accounts = accounts; this.trading = trading;
    }

    @Override
    public Optional<View> show(Long itemId, Long viewerId) {
        Item item = items.findById(itemId).orElse(null);
        if (item == null) return Optional.empty();
        var seller = accounts.findPublic(item.getSellerId()).orElse(null);
        if (seller == null) return Optional.empty();
        boolean owner = viewerId != null && viewerId.equals(item.getSellerId());
        boolean hidden = item.getModerationStatus() != ItemModerationStatus.VISIBLE
                || item.getStatus() == ItemStatus.WITHDRAWN || !"ACTIVE".equals(seller.status());
        if (hidden && !owner) return Optional.empty();

        var inquiry = viewerId == null || owner ? Optional.<TradingInquiryPort.Inquiry>empty()
                : trading.activeInquiry(itemId, viewerId);
        List<Action> actions = new ArrayList<>();
        if (owner) actions.add(Action.MANAGE_LISTING);
        else if (item.getStatus() == ItemStatus.ON_SALE) {
            actions.add(Action.CHAT_SELLER);
            actions.add(inquiry.isPresent() ? Action.VIEW_PURCHASE_REQUEST : Action.REQUEST_PURCHASE);
            if (viewerId != null) actions.add(Action.REPORT_ITEM);
        } else if (viewerId != null) {
            actions.add(Action.REPORT_ITEM);
            if (inquiry.isPresent()) actions.add(Action.VIEW_PURCHASE_REQUEST);
        }

        var sellerView = new Seller(seller.id(), displayName(seller), seller.region(), seller.creditScore(), seller.lastActiveAt(),
                items.countBySellerIdAndStatusAndModerationStatus(seller.id(), ItemStatus.ON_SALE,
                        ItemModerationStatus.VISIBLE));
        var request = inquiry.map(value -> new PurchaseRequest(value.id(), value.status(), parse(value.expiresAt()))).orElse(null);
        var viewer = new Viewer(viewerId != null, owner, List.copyOf(actions), request);
        var related = items.findTop4BySellerIdAndStatusAndModerationStatusAndIdNotOrderByCreatedAtDesc(
                        seller.id(), ItemStatus.ON_SALE, ItemModerationStatus.VISIBLE, itemId)
                .stream().map(value -> new RelatedItem(value.getId(), value.getTitle(), value.getPrice(),
                        value.getImageUrl(), value.getRegion(), Set.copyOf(value.getTags()))).toList();
        return Optional.of(new View(item.getId(), item.getTitle(), item.getCategory(), item.getPrice(),
                item.getDescription(), item.getImageUrl(), item.getRegion(), Set.copyOf(item.getTags()),
                item.getStatus(), item.getModerationStatus(), item.getCreatedAt(), item.getSellerId(),
                sellerView, viewer, related));
    }

    private String displayName(AccountPublicPort.PublicAccount account) {
        return account.nickname() == null || account.nickname().isBlank() ? account.username() : account.nickname();
    }
    private LocalDateTime parse(String value) {
        try { return value == null ? null : LocalDateTime.parse(value); }
        catch (DateTimeParseException ignored) { return null; }
    }
}
