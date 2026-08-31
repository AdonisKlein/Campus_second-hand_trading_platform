package com.campus.secondhand.trading;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface TradingWorkflow {
    OrderView requestPurchase(CurrentActor actor, long itemId);
    OrderView perform(CurrentActor actor, long orderId, OrderAction action);
    List<OrderView> list(CurrentActor actor);
    Desk browse(CurrentActor actor, Perspective perspective, Stage stage);
    OrderView applyMarketplaceResult(MarketplaceResult result);
    boolean expire(long orderId);
    Optional<OrderView> activeInquiry(long itemId, long buyerId);

    enum Perspective { BUYING, SELLING }
    enum Stage { REQUESTS, HANDOVER, CLOSED, ALL }
    enum StepState { COMPLETE, CURRENT, UPCOMING, STOPPED }
    record OrderView(long id, long itemId, String itemTitle, BigDecimal itemPrice,
                     long buyerId, String buyerNickname, long sellerId, String sellerNickname,
                     OrderStatus status, LocalDateTime expiresAt, String closureReason,
                     LocalDateTime createdAt, List<OrderAction> allowedActions) {}
    record Desk(Perspective perspective, Stage stage, Summary summary, List<ItemGroup> groups) {}
    record Summary(long requests, long handovers, long closed, long actionable) {}
    record ItemGroup(long itemId, String itemTitle, BigDecimal itemPrice, List<Entry> entries) {}
    record Entry(long id, long itemId, String itemTitle, BigDecimal itemPrice,
                 long buyerId, String buyerNickname, long sellerId, String sellerNickname,
                 PublicCounterparty counterparty, OrderStatus status, LocalDateTime expiresAt,
                 String closureReason, LocalDateTime createdAt, LocalDateTime updatedAt,
                 List<OrderAction> allowedActions, List<TimelineStep> timeline) {}
    record PublicCounterparty(long id, String nickname, String campusRegion, Integer creditScore,
                              LocalDateTime lastActiveAt) {}
    record TimelineStep(String code, String label, String hint, StepState state, LocalDateTime occurredAt) {}
    record MarketplaceResult(String eventId, String type, long orderId, long itemId, String reason) {}
}
