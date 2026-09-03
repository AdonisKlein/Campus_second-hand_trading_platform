package com.campus.secondhand.gateway;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webtestclient.autoconfigure.AutoConfigureWebTestClient;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.reactive.server.EntityExchangeResult;
import org.springframework.test.web.reactive.server.WebTestClient;
import reactor.core.publisher.Mono;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureWebTestClient
@ActiveProfiles("test")
class GatewayAuthWebTest {
    @Autowired
    private WebTestClient client;

    @MockitoBean
    private AccountClient accounts;

    @Test
    void publicLoginRequiresCsrfToken() {
        client.post().uri("/api/auth/login")
                .bodyValue("{\"email\":\"student@example.com\",\"password\":\"abc123\"}")
                .header(HttpHeaders.CONTENT_TYPE, "application/json")
                .exchange()
                .expectStatus().isForbidden()
                .expectBody()
                .jsonPath("$.success").isEqualTo(false);
    }

    @Test
    void correlationIdAndVersionAreObservableWithoutAuthentication() {
        client.get().uri("/api/auth/csrf")
                .header("X-Correlation-Id", "course-check-42")
                .exchange()
                .expectStatus().isOk()
                .expectHeader().valueEquals("X-Correlation-Id", "course-check-42");

        client.get().uri("/actuator/info")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.app.name").isEqualTo("campus-api-gateway")
                .jsonPath("$.app.version").isEqualTo("dev")
                .jsonPath("$.app.commit").isEqualTo("local");
    }

    @Test
    void csrfLoginSessionAndRevocationUseStableJsonContract() {
        AuthenticatedAccount account = account(7);
        when(accounts.authenticate(any(LoginCredentials.class))).thenReturn(Mono.just(account));
        when(accounts.securityState(42L)).thenReturn(Mono.just(new AccountSecurityState(
                42L, "ACTIVE", "STUDENT", 7)));

        AtomicReference<String> token = new AtomicReference<>();
        EntityExchangeResult<byte[]> csrf = client.get().uri("/api/auth/csrf")
                .exchange()
                .expectStatus().isOk()
                .expectCookie().exists("XSRF-TOKEN")
                .expectBody()
                .jsonPath("$.success").isEqualTo(true)
                .jsonPath("$.data").value(value -> token.set(String.valueOf(value)))
                .returnResult();

        String xsrfCookie = csrf.getResponseCookies().getFirst("XSRF-TOKEN").getValue();
        EntityExchangeResult<byte[]> login = client.post().uri("/api/auth/login")
                .cookie("XSRF-TOKEN", xsrfCookie)
                .header("X-XSRF-TOKEN", token.get())
                .header(HttpHeaders.CONTENT_TYPE, "application/json")
                .bodyValue("{\"email\":\"student@example.com\",\"password\":\"abc123\"}")
                .exchange()
                .expectStatus().isOk()
                .expectCookie().exists("SESSION")
                .expectBody()
                .jsonPath("$.success").isEqualTo(true)
                .jsonPath("$.data.id").isEqualTo(42)
                .jsonPath("$.data.authVersion").doesNotExist()
                .returnResult();

        String sessionCookie = login.getResponseCookies().getFirst("SESSION").getValue();
        when(accounts.securityState(42L)).thenReturn(Mono.just(new AccountSecurityState(
                42L, "DISABLED", "STUDENT", 8)));
        client.get().uri("/api/orders")
                .cookie("SESSION", sessionCookie)
                .exchange()
                .expectStatus().isUnauthorized()
                .expectBody()
                .jsonPath("$.success").isEqualTo(false);
    }

    @Test
    void accountFailureReturnsRetryableServiceUnavailable() {
        when(accounts.authenticate(any(LoginCredentials.class))).thenReturn(Mono.error(
                new AccountClientException(AccountClientException.Kind.UNAVAILABLE, null)));
        Csrf csrf = csrf();

        client.post().uri("/api/auth/login")
                .cookie("XSRF-TOKEN", csrf.cookie())
                .header("X-XSRF-TOKEN", csrf.token())
                .header(HttpHeaders.CONTENT_TYPE, "application/json")
                .bodyValue("{\"email\":\"student@example.com\",\"password\":\"abc123\"}")
                .exchange()
                .expectStatus().isEqualTo(HttpStatus.SERVICE_UNAVAILABLE)
                .expectHeader().valueEquals(HttpHeaders.RETRY_AFTER, "1")
                .expectBody()
                .jsonPath("$.success").isEqualTo(false);
    }

    @Test
    void logoutEndpointRequiresAuthenticatedSession() {
        client.post().uri("/api/auth/logout")
                .exchange().expectStatus().isUnauthorized();
    }

    @Test
    void loginValidationHasStableContract() {
        Csrf invalidCsrf = csrf();
        client.post().uri("/api/auth/login")
                .cookie("XSRF-TOKEN", invalidCsrf.cookie())
                .header("X-XSRF-TOKEN", invalidCsrf.token())
                .header(HttpHeaders.CONTENT_TYPE, "application/json")
                .bodyValue("{\"email\":\"bad\",\"password\":\"\"}")
                .exchange().expectStatus().isBadRequest();

    }

    private Csrf csrf() {
        AtomicReference<String> token = new AtomicReference<>();
        EntityExchangeResult<byte[]> result = client.get().uri("/api/auth/csrf")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.data").value(value -> token.set(String.valueOf(value)))
                .returnResult();
        return new Csrf(result.getResponseCookies().getFirst("XSRF-TOKEN").getValue(), token.get());
    }

    private AuthenticatedAccount account(long authVersion) {
        return new AuthenticatedAccount(42L, "student@example.com", "student", "学生",
                null, "STUDENT", "ACTIVE", "学院路校区", 100,
                LocalDateTime.of(2026, 8, 27, 12, 0), authVersion);
    }

    private record Csrf(String cookie, String token) {}
}
