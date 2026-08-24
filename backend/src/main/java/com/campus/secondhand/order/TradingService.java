package com.campus.secondhand.order;

import com.campus.secondhand.item.*;
import com.campus.secondhand.user.*;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class TradingService {
    private static final List<OrderStatus> ACTIVE = List.of(
        OrderStatus.PURCHASE_REQUESTED, OrderStatus.WAITING_HANDOVER);
    private final TradeOrderRepository orders;
    private final ItemRepository items;
    private final UserRepository users;
    private final Clock clock;
    private final long requestMinutes;
    private final long handoverMinutes;

    public TradingService(TradeOrderRepository orders, ItemRepository items, UserRepository users, Clock clock,
                          @Value("${app.trading.purchase-request-minutes:1440}") long requestMinutes,
                          @Value("${app.trading.handover-minutes:4320}") long handoverMinutes) {
        this.orders = orders;
        this.items = items;
        this.users = users;
        this.clock = clock;
        this.requestMinutes = requestMinutes;
        this.handoverMinutes = handoverMinutes;
    }

    @Transactional
    public OrderView requestPurchase(Long buyerId, Long itemId) {
        User buyer = activeUser(buyerId, "买家不存在");
        Item item = items.findLockedById(itemId).orElseThrow(() -> new TradingRuleException("商品不存在"));
        if (buyerId.equals(item.getSellerId())) throw new TradingRuleException("不能购买自己发布的商品");
        if (item.getStatus() != ItemStatus.ON_SALE || item.getModerationStatus() != ItemModerationStatus.VISIBLE) {
            throw new TradingRuleException("商品当前不可下单");
        }
        User seller = activeUser(item.getSellerId(), "卖家账号不可用");
        orders.findFirstByItemIdAndBuyerIdAndStatusIn(itemId, buyerId, ACTIVE).ifPresent(existing -> {
            if (!existing.getExpiresAt().isAfter(now())) expire(existing, item);
            else throw new TradingRuleException("你已经提交过购买意向，请在订单页等待卖家回应");
        });

        LocalDateTime now = now();
        TradeOrder order = new TradeOrder();
        order.setItemId(item.getId());
        order.setBuyerId(buyerId);
        order.setSellerId(seller.getId());
        order.setItemTitle(item.getTitle());
        order.setItemPrice(item.getPrice());
        order.setBuyerNickname(displayName(buyer));
        order.setSellerNickname(displayName(seller));
        order.setExpiresAt(now.plusMinutes(requestMinutes));
        return view(orders.save(order), buyerId);
    }

    @Transactional(readOnly = true)
    public List<OrderView> listOrders(Long actorId) {
        return orders.findByBuyerIdOrSellerIdOrderByCreatedAtDesc(actorId, actorId).stream()
            .map(order -> view(order, actorId)).toList();
    }

    @Transactional
    public OrderView perform(Long actorId, Long orderId, OrderAction action) {
        TradeOrder snapshot = orders.findById(orderId)
            .orElseThrow(() -> new TradingRuleException("订单不存在"));
        Item item = items.findLockedById(snapshot.getItemId())
            .orElseThrow(() -> new TradingRuleException("订单对应的商品不存在"));
        TradeOrder order = orders.findLockedById(orderId)
            .orElseThrow(() -> new TradingRuleException("订单不存在"));
        if (!actorId.equals(order.getBuyerId()) && !actorId.equals(order.getSellerId())) {
            throw new TradingRuleException("当前不能执行这个操作");
        }
        if (ACTIVE.contains(order.getStatus()) && !order.getExpiresAt().isAfter(now())) {
            expire(order, item);
            return view(order, actorId);
        }
        if (!allowedActions(order, actorId).contains(action)) throw new TradingRuleException("当前不能执行这个操作");

        switch (action) {
            case ACCEPT -> accept(order, item);
            case DECLINE -> {
                order.setStatus(OrderStatus.DECLINED);
                order.setClosureReason("卖家暂未接受这次购买意向");
            }
            case COMPLETE -> {
                order.setStatus(OrderStatus.COMPLETED);
                item.setStatus(ItemStatus.SOLD);
            }
            case CANCEL -> {
                order.setStatus(OrderStatus.CANCELLED);
                order.setClosureReason(actorId.equals(order.getBuyerId()) ? "买家取消" : "卖家取消");
                if (item.getStatus() == ItemStatus.RESERVED) item.setStatus(ItemStatus.ON_SALE);
            }
        }
        items.save(item);
        return view(orders.save(order), actorId);
    }

    @Transactional
    public boolean expireOrder(Long orderId) {
        TradeOrder snapshot = orders.findById(orderId).orElse(null);
        if (snapshot == null) return false;
        Item item = items.findLockedById(snapshot.getItemId()).orElse(null);
        TradeOrder order = orders.findLockedById(orderId).orElse(null);
        if (item == null || order == null || !ACTIVE.contains(order.getStatus())
            || order.getExpiresAt().isAfter(now())) return false;
        expire(order, item);
        return true;
    }

    @Transactional(readOnly = true)
    public List<Long> overdueOrderIds() {
        return orders.findByStatusInAndExpiresAtBefore(ACTIVE, now()).stream().map(TradeOrder::getId).toList();
    }

    private void accept(TradeOrder order, Item item) {
        if (item.getStatus() != ItemStatus.ON_SALE || item.getModerationStatus() != ItemModerationStatus.VISIBLE) {
            throw new TradingRuleException("商品当前不能接受购买意向");
        }
        order.setStatus(OrderStatus.WAITING_HANDOVER);
        order.setExpiresAt(now().plusMinutes(handoverMinutes));
        item.setStatus(ItemStatus.RESERVED);
        for (TradeOrder other : orders.findByItemIdAndStatus(item.getId(), OrderStatus.PURCHASE_REQUESTED)) {
            if (other.getId().equals(order.getId())) continue;
            if (other.getExpiresAt().isAfter(now())) {
                other.setStatus(OrderStatus.DECLINED);
                other.setClosureReason("卖家已选择其他买家");
            } else {
                other.setStatus(OrderStatus.EXPIRED);
                other.setClosureReason("卖家未在有效期内回应");
            }
            orders.save(other);
        }
    }

    private void expire(TradeOrder order, Item item) {
        boolean heldReservation = order.getStatus() == OrderStatus.WAITING_HANDOVER;
        order.setStatus(OrderStatus.EXPIRED);
        if (heldReservation && item.getStatus() == ItemStatus.RESERVED) {
            item.setStatus(ItemStatus.ON_SALE);
            items.save(item);
        }
        order.setClosureReason("交易阶段已超过有效期");
        orders.save(order);
    }

    private List<OrderAction> allowedActions(TradeOrder order, Long actorId) {
        List<OrderAction> result = new ArrayList<>();
        if (order.getStatus() == OrderStatus.PURCHASE_REQUESTED) {
            if (!order.getExpiresAt().isAfter(now())) return List.of();
            if (actorId.equals(order.getSellerId())) result.add(OrderAction.ACCEPT);
            if (actorId.equals(order.getSellerId())) result.add(OrderAction.DECLINE);
            if (actorId.equals(order.getBuyerId())) result.add(OrderAction.CANCEL);
        } else if (order.getStatus() == OrderStatus.WAITING_HANDOVER) {
            if (!order.getExpiresAt().isAfter(now())) return List.of();
            if (actorId.equals(order.getBuyerId())) result.add(OrderAction.COMPLETE);
            if (actorId.equals(order.getBuyerId()) || actorId.equals(order.getSellerId())) result.add(OrderAction.CANCEL);
        }
        return List.copyOf(result);
    }

    private OrderView view(TradeOrder order, Long actorId) {
        return new OrderView(order.getId(), order.getItemId(), order.getItemTitle(), order.getItemPrice(),
            order.getBuyerId(), order.getBuyerNickname(), order.getSellerId(), order.getSellerNickname(),
            order.getStatus(), order.getExpiresAt(), order.getClosureReason(), order.getCreatedAt(), allowedActions(order, actorId));
    }

    private User activeUser(Long id, String error) {
        User user = users.findById(id).orElseThrow(() -> new TradingRuleException(error));
        if (!"ACTIVE".equals(user.getStatus())) throw new TradingRuleException(error);
        return user;
    }

    private String displayName(User user) {
        return user.getNickname() == null || user.getNickname().isBlank() ? user.getUsername() : user.getNickname();
    }

    private LocalDateTime now() { return LocalDateTime.now(clock); }
}
