package com.campus.secondhand.order;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/** Read model for the buyer/seller order workspace. */
public interface TradeDesk {
    Desk browse(Long actorId, Perspective perspective, Stage stage);

    enum Perspective { BUYING, SELLING }
    enum Stage { REQUESTS, HANDOVER, CLOSED, ALL }
    enum StepState { COMPLETE, CURRENT, UPCOMING, STOPPED }

    record Desk(Perspective perspective, Stage stage, Summary summary, List<ItemGroup> groups) {}
    record Summary(long requests, long handovers, long closed, long actionable) {}
    record ItemGroup(Long itemId, String itemTitle, BigDecimal itemPrice, List<Entry> entries) {}
    record Entry(Long id, Long itemId, String itemTitle, BigDecimal itemPrice,
                 Long buyerId, String buyerNickname, Long sellerId, String sellerNickname,
                 PublicCounterparty counterparty, OrderStatus status, LocalDateTime expiresAt,
                 String closureReason, LocalDateTime createdAt, LocalDateTime updatedAt,
                 List<OrderAction> allowedActions, List<TimelineStep> timeline) {}
    record PublicCounterparty(Long id, String nickname, String campusRegion, Integer creditScore,
                              LocalDateTime lastActiveAt) {}
    record TimelineStep(String code, String label, String hint, StepState state, LocalDateTime occurredAt) {}
}
