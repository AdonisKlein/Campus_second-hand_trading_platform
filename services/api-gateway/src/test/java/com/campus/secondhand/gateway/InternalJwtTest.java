package com.campus.secondhand.gateway;

import static org.assertj.core.api.Assertions.assertThat;
import org.junit.jupiter.api.Test;

class InternalJwtTest {
    @Test
    void tokenContainsGatewayIssuerSubjectAndAuthVersion() {
        GatewayProperties properties = new GatewayProperties("http://account", "redis://localhost",
                "service-token-012345678901234567890123", "01234567890123456789012345678901",
                java.util.List.of("http://localhost:3000"), java.util.List.of("Content-Type"), false);
        String token = new InternalJwt(properties).issue(new AuthenticatedAccount(42L, "s@x.test", "student",
                "小明", null, "STUDENT", "ACTIVE", null, 100, null, 7));
        String payload = new String(java.util.Base64.getUrlDecoder().decode(token.split("\\.")[1]), java.nio.charset.StandardCharsets.UTF_8);
        assertThat(payload).contains("\"iss\":\"campus-gateway\"").contains("\"sub\":\"42\"").contains("\"auth_version\":7");
        assertThat(token.split("\\.")).hasSize(3);
    }
}
