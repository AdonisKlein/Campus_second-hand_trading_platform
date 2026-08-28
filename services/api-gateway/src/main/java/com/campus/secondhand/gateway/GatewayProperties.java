package com.campus.secondhand.gateway;

import java.nio.charset.StandardCharsets;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "campus.gateway")
public record GatewayProperties(String accountUri, String redisUrl,
                                String internalServiceToken, String internalJwtSecret,
                                List<String> corsOrigins, List<String> corsAllowedHeaders,
                                boolean secureCookies) {
    public GatewayProperties {
        requireSecret("internal-jwt-secret", internalJwtSecret);
        requireSecret("internal-service-token", internalServiceToken);
        if (corsOrigins == null || corsOrigins.isEmpty()
                || corsOrigins.stream().anyMatch(origin -> origin.isBlank() || origin.contains("*")
                        || !origin.matches("https?://[^*\\s]+"))) {
            throw new IllegalArgumentException("campus.gateway.cors-origins must contain exact origins");
        }
        if (corsAllowedHeaders == null || corsAllowedHeaders.isEmpty()
                || corsAllowedHeaders.stream().anyMatch(header -> header.isBlank() || header.contains("*"))) {
            throw new IllegalArgumentException("campus.gateway.cors-allowed-headers must be explicit");
        }
    }

    private static void requireSecret(String name, String value) {
        if (value == null || value.getBytes(StandardCharsets.UTF_8).length < 32) {
            throw new IllegalArgumentException("campus.gateway." + name + " must contain at least 32 UTF-8 bytes");
        }
    }
}
