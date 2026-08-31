package com.campus.secondhand.trading;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("campus.trading")
public record TradingProperties(
        String accountUri,
        String marketplaceUri,
        String internalServiceToken,
        String internalJwtSecret,
        long purchaseRequestMinutes,
        long handoverMinutes,
        boolean messagingEnabled,
        int dependencyConnectTimeoutMs,
        int dependencyResponseTimeoutMs) {
}
