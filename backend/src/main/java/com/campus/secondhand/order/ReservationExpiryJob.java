package com.campus.secondhand.order;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class ReservationExpiryJob {
    private final TradingService trading;

    public ReservationExpiryJob(TradingService trading) { this.trading = trading; }

    @Scheduled(fixedDelayString = "${app.trading.expiry-scan-ms:60000}")
    public void releaseExpiredReservations() {
        trading.overdueReservationIds().forEach(trading::expireReservation);
    }
}
