package com.campus.secondhand.marketplace;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "campus.marketplace")
public record MarketplaceProperties(String accountUri, String tradingUri, String internalServiceToken,
                                    String internalJwtSecret, String uploadDir, int dependencyConnectTimeoutMs,
                                    int dependencyResponseTimeoutMs) {
    public MarketplaceProperties {
        if (accountUri == null || tradingUri == null || internalServiceToken == null
                || internalServiceToken.length() < 32 || internalJwtSecret == null
                || internalJwtSecret.length() < 32 || uploadDir == null || uploadDir.isBlank()
                || dependencyConnectTimeoutMs < 100 || dependencyResponseTimeoutMs < 100)
            throw new IllegalArgumentException("Marketplace remote configuration is required");
    }
}
