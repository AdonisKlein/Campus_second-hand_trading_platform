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
        OrderStatus.PENDING_SELLER_CONFIRMATION, OrderStatus.WAITING_HANDOVER);
    private final TradeOrderRepository orders;
    private final ItemRepository items;
    private final UserRepository users;
    private final Clock clock;
    private final long reservationMinutes;

    public TradingService(TradeOrderRepository orders, ItemRepository items, UserRepository users, Clock clock,
                          @Value("${app.trading.reservation-minutes:30}") long reservationMinutes) {
        this.orders = orders;
        this.items = items;
        this.users = users;
        this.clock = clock;
        this.reservationMinutes = reservationMinutes;
    }

    @Transactional
    public OrderView placeOrder(Long buyerId, Long itemId) {
        User buyer = activeUser(buyerId, "买家不存在");
        Item item = items.findLockedById(itemId).orElseThrow(() -> new TradingRuleException("商品不存在"));
        releaseExpiredReservation(item);
        if (buyerId.equals(item.getSellerId())) throw new TradingRuleException("不能购买自己发布的商品");
        if (item.getStatus() != ItemStatus.ON_SALE || item.getModerationStatus() != ItemModerationStatus.VISIBLE) {
            throw new TradingRuleException("商品当前不可下单");
        }
        User seller = activeUser(item.getSellerId(), "卖家账号不可用");

        LocalDateTime now = now();
        TradeOrder order = new TradeOrder();
        order.setItemId(item.getId());
        order.setBuyerId(buyerId);
        order.setSellerId(seller.getId());
        order.setItemTitle(item.getTitle());
        order.setItemPrice(item.getPrice());
        order.setBuyerNickname(displayName(buyer));
        order.setSellerNickname(displayName(seller));
        order.setReservationExpiresAt(now.plusMinutes(reservationMinutes));
        item.setStatus(ItemStatus.RESERVED);
        items.save(item);
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
        if (order.getStatus() == OrderStatus.PENDING_SELLER_CONFIRMATION
            && !order.getReservationExpiresAt().isAfter(now())) {
            expire(order, item);
            return view(order, actorId);
        }
        if (!allowedActions(order, actorId).contains(action)) throw new TradingRuleException("当前不能执行这个操作");

        switch (action) {
            case ACCEPT -> order.setStatus(OrderStatus.WAITING_HANDOVER);
            case COMPLETE -> {
                order.setStatus(OrderStatus.COMPLETED);
                item.setStatus(ItemStatus.SOLD);
            }
            case CANCEL -> {
                order.setStatus(OrderStatus.CANCELLED);
                if (item.getStatus() == ItemStatus.RESERVED) item.setStatus(ItemStatus.ON_SALE);
            }
        }
        items.save(item);
        return view(orders.save(order), actorId);
    }

    @Transactional
    public boolean expireReservation(Long orderId) {
        TradeOrder snapshot = orders.findById(orderId).orElse(null);
        if (snapshot == null) return false;
        Item item = items.findLockedById(snapshot.getItemId()).orElse(null);
        TradeOrder order = orders.findLockedById(orderId).orElse(null);
        if (item == null || order == null || order.getStatus() != OrderStatus.PENDING_SELLER_CONFIRMATION
            || order.getReservationExpiresAt().isAfter(now())) return false;
        expire(order, item);
        return true;
    }

    @Transactional(readOnly = true)
    public List<Long> overdueReservationIds() {
        return orders.findByStatusAndReservationExpiresAtBefore(
            OrderStatus.PENDING_SELLER_CONFIRMATION, now()).stream().map(TradeOrder::getId).toList();
    }

    private void releaseExpiredReservation(Item item) {
        orders.findFirstByItemIdAndStatusIn(item.getId(), ACTIVE).ifPresent(order -> {
            if (order.getStatus() == OrderStatus.PENDING_SELLER_CONFIRMATION
                && !order.getReservationExpiresAt().isAfter(now())) expire(order, item);
        });
    }

    private void expire(TradeOrder order, Item item) {
        order.setStatus(OrderStatus.EXPIRED);
        if (item.getStatus() == ItemStatus.RESERVED) item.setStatus(ItemStatus.ON_SALE);
        orders.save(order);
        items.save(item);
    }

    private List<OrderAction> allowedActions(TradeOrder order, Long actorId) {
        List<OrderAction> result = new ArrayList<>();
        if (order.getStatus() == OrderStatus.PENDING_SELLER_CONFIRMATION) {
            if (!order.getReservationExpiresAt().isAfter(now())) return List.of();
            if (actorId.equals(order.getSellerId())) result.add(OrderAction.ACCEPT);
            if (actorId.equals(order.getBuyerId()) || actorId.equals(order.getSellerId())) result.add(OrderAction.CANCEL);
        } else if (order.getStatus() == OrderStatus.WAITING_HANDOVER) {
            if (actorId.equals(order.getBuyerId())) result.add(OrderAction.COMPLETE);
            if (actorId.equals(order.getBuyerId()) || actorId.equals(order.getSellerId())) result.add(OrderAction.CANCEL);
        }
        return List.copyOf(result);
    }

    private OrderView view(TradeOrder order, Long actorId) {
        return new OrderView(order.getId(), order.getItemId(), order.getItemTitle(), order.getItemPrice(),
            order.getBuyerId(), order.getBuyerNickname(), order.getSellerId(), order.getSellerNickname(),
            order.getStatus(), order.getReservationExpiresAt(), order.getCreatedAt(), allowedActions(order, actorId));
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
