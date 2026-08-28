package com.campus.secondhand.trading;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
class ReservationExpiryJob {
    private final TradeOrderRepository orders; private final TradingWorkflow workflow; private final Clock clock;
    ReservationExpiryJob(TradeOrderRepository orders,TradingWorkflow workflow,Clock clock){this.orders=orders;this.workflow=workflow;this.clock=clock;}
    @Scheduled(fixedDelayString="${campus.trading.expiry-scan-ms:60000}")
    void expire(){List<Long> ids=orders.findByStatusInAndExpiresAtBefore(List.of(OrderStatus.PURCHASE_REQUESTED,OrderStatus.WAITING_HANDOVER),LocalDateTime.now(clock)).stream().map(TradeOrder::getId).toList();ids.forEach(workflow::expire);}
}
