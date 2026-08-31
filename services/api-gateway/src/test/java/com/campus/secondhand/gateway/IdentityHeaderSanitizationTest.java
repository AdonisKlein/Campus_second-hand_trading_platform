package com.campus.secondhand.gateway;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

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

    @Test
    void publicItemReadKeepsAnonymousAccessButForwardsVerifiedSessionIdentity() {
        AccountClient accounts = mock(AccountClient.class);
        InternalJwt jwt = mock(InternalJwt.class);
        AuthenticatedAccount account = new AuthenticatedAccount(42L, "student@example.com", "student", "学生",
                null, "STUDENT", "ACTIVE", "学院路校区", 100,
                LocalDateTime.of(2026, 8, 29, 12, 0), 7);
        when(accounts.securityState(42L)).thenReturn(Mono.just(
                new AccountSecurityState(42L, "ACTIVE", "STUDENT", 7)));
        when(jwt.issue(account)).thenReturn("verified-internal-jwt");
        GatewaySecurityWebFilter filter = new GatewaySecurityWebFilter(accounts, jwt);
        AtomicReference<ServerWebExchange> forwarded = new AtomicReference<>();
        WebFilterChain chain = secured -> { forwarded.set(secured); return Mono.empty(); };

        MockServerWebExchange anonymous = MockServerWebExchange.from(MockServerHttpRequest.get("/api/items/9"));
        StepVerifier.create(filter.filter(anonymous, chain)).verifyComplete();
        assertThat(forwarded.get().getRequest().getHeaders().containsHeader(HttpHeaders.AUTHORIZATION)).isFalse();

        MockServerWebExchange exchange = MockServerWebExchange.from(MockServerHttpRequest.get("/api/items/9"));
        exchange.getSession().block().getAttributes().put(GatewaySecurityWebFilter.SESSION_ACCOUNT, account);

        StepVerifier.create(filter.filter(exchange, chain)).verifyComplete();

        assertThat(forwarded.get().getRequest().getHeaders().getFirst(HttpHeaders.AUTHORIZATION))
                .isEqualTo("Bearer verified-internal-jwt");
    }
}
