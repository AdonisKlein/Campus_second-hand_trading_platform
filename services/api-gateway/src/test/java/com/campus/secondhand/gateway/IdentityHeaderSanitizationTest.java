package com.campus.secondhand.gateway;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;

class IdentityHeaderSanitizationTest {
    @Test
    void removesEveryClientSuppliedIdentityHeader() {
        MockServerWebExchange exchange = MockServerWebExchange.from(MockServerHttpRequest.get("/api/items")
                .header(HttpHeaders.AUTHORIZATION, "Bearer attacker")
                .header("X-User-Id", "99")
                .header("X-User-Role", "ADMIN")
                .header("X-Admin-Id", "1")
                .header("X-Internal-Service-Token", "attacker"));

        HttpHeaders headers = GatewaySecurityWebFilter.withoutClientIdentity(exchange)
                .getRequest().getHeaders();

        assertThat(headers.containsHeader(HttpHeaders.AUTHORIZATION)).isFalse();
        assertThat(headers.containsHeader("X-User-Id")).isFalse();
        assertThat(headers.containsHeader("X-User-Role")).isFalse();
        assertThat(headers.containsHeader("X-Admin-Id")).isFalse();
        assertThat(headers.containsHeader("X-Internal-Service-Token")).isFalse();
    }
}
