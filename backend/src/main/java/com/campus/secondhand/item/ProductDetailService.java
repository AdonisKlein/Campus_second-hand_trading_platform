package com.campus.secondhand.item;

import com.campus.secondhand.order.OrderStatus;
import com.campus.secondhand.order.TradeOrder;
import com.campus.secondhand.order.TradeOrderRepository;
import com.campus.secondhand.user.User;
import com.campus.secondhand.user.UserRepository;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class ProductDetailService implements ProductDetail {
    private static final List<OrderStatus> ACTIVE_REQUESTS = List.of(
        OrderStatus.PURCHASE_REQUESTED, OrderStatus.WAITING_HANDOVER);

    private final ItemRepository items;
    private final UserRepository users;
    private final TradeOrderRepository orders;
    private final Clock clock;

    public ProductDetailService(ItemRepository items, UserRepository users,
                                TradeOrderRepository orders, Clock clock) {
        this.items = items;
        this.users = users;
        this.orders = orders;
        this.clock = clock;
    }

    @Override
    public Optional<View> show(Long itemId, Long viewerId) {
        Item item = items.findById(itemId).orElse(null);
        if (item == null) return Optional.empty();

        User seller = users.findById(item.getSellerId()).orElse(null);
        if (seller == null) return Optional.empty();
        User viewerUser = viewerId == null ? null : users.findById(viewerId).orElse(null);
        boolean owner = viewerId != null && viewerId.equals(item.getSellerId());
        boolean hidden = item.getModerationStatus() != ItemModerationStatus.VISIBLE
            || item.getStatus() == ItemStatus.WITHDRAWN || !"ACTIVE".equals(seller.getStatus());
        if (hidden && !owner) return Optional.empty();
        boolean student = viewerUser != null && "STUDENT".equals(viewerUser.getRole());
        TradeOrder active = student && !owner ? activeRequest(item.getId(), viewerId).orElse(null) : null;

        List<Action> actions = new ArrayList<>();
        if (owner) {
            actions.add(Action.MANAGE_LISTING);
        } else if (item.getStatus() == ItemStatus.ON_SALE) {
            if (viewerUser == null || student) actions.add(Action.CHAT_SELLER);
            if (active == null && (viewerUser == null || student)) actions.add(Action.REQUEST_PURCHASE);
            if (active != null) actions.add(Action.VIEW_PURCHASE_REQUEST);
            if (student) actions.add(Action.REPORT_ITEM);
        } else if (student) {
            actions.add(Action.REPORT_ITEM);
            if (active != null) actions.add(Action.VIEW_PURCHASE_REQUEST);
        }

        long onSaleCount = items.countBySellerIdAndStatusAndModerationStatus(
            seller.getId(), ItemStatus.ON_SALE, ItemModerationStatus.VISIBLE);
        Seller sellerView = new Seller(seller.getId(), displayName(seller), seller.getCampusRegion(),
            seller.getCreditScore(), seller.getLastActiveAt(), onSaleCount);
        PurchaseRequest request = active == null ? null
            : new PurchaseRequest(active.getId(), active.getStatus(), active.getExpiresAt());
        Viewer viewer = new Viewer(viewerUser != null, owner, List.copyOf(actions), request);
        List<RelatedItem> related = items
            .findTop4BySellerIdAndStatusAndModerationStatusAndIdNotOrderByCreatedAtDesc(
                seller.getId(), ItemStatus.ON_SALE, ItemModerationStatus.VISIBLE, item.getId())
            .stream().map(this::related).toList();

        return Optional.of(new View(item.getId(), item.getTitle(), item.getCategory(), item.getPrice(),
            item.getDescription(), item.getImageUrl(), item.getRegion(), Set.copyOf(item.getTags()),
            item.getStatus(), item.getModerationStatus(), item.getCreatedAt(), item.getSellerId(), sellerView, viewer, related));
    }

    private Optional<TradeOrder> activeRequest(Long itemId, Long viewerId) {
        LocalDateTime now = LocalDateTime.now(clock);
        return orders.findFirstByItemIdAndBuyerIdAndStatusIn(itemId, viewerId, ACTIVE_REQUESTS)
            .filter(order -> order.getExpiresAt().isAfter(now));
    }

    private RelatedItem related(Item item) {
        return new RelatedItem(item.getId(), item.getTitle(), item.getPrice(), item.getImageUrl(),
            item.getRegion(), Set.copyOf(item.getTags()));
    }

    private String displayName(User user) {
        return user.getNickname() == null || user.getNickname().isBlank()
            ? user.getUsername() : user.getNickname();
    }
}
