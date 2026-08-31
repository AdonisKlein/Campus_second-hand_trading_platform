package com.campus.secondhand.governance;

import java.nio.charset.StandardCharsets;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "campus.governance")
public record GovernanceProperties(String accountUri, String marketplaceUri,
                                   String internalServiceToken, String internalJwtSecret,
                                   boolean messagingEnabled, int dependencyConnectTimeoutMs,
                                   int dependencyResponseTimeoutMs) {
    public GovernanceProperties {
        requireSecret("internal-service-token", internalServiceToken);
        requireSecret("internal-jwt-secret", internalJwtSecret);
        if (dependencyConnectTimeoutMs < 100 || dependencyResponseTimeoutMs < 100) {
            throw new IllegalArgumentException("campus.governance dependency timeouts 不能小于 100");
        }
    }

    private static void requireSecret(String name, String value) {
        if (value == null || value.getBytes(StandardCharsets.UTF_8).length < 32) {
            throw new IllegalArgumentException("campus.governance." + name + " 至少需要 32 字节");
        }
    }
}
