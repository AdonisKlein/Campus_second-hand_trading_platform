package com.campus.secondhand.gateway;

import jakarta.validation.Valid;
import java.time.LocalDateTime;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.web.server.csrf.CsrfToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

@RestController
public class GatewayAuthController {
    private final AccountClient accounts;

    public GatewayAuthController(AccountClient accounts) {
        this.accounts = accounts;
    }

    @GetMapping("/api/auth/csrf")
    public Mono<ApiResponse<String>> csrf(ServerWebExchange exchange) {
        Mono<CsrfToken> token = exchange.getAttribute(CsrfToken.class.getName());
        if (token == null) {
            return Mono.error(new IllegalStateException("CSRF token was not initialized"));
        }
        return token.map(value -> ApiResponse.ok(value.getToken()));
    }

    @PostMapping("/api/auth/login")
    public Mono<ResponseEntity<ApiResponse<UserView>>> login(
            @Valid @RequestBody LoginCredentials credentials, ServerWebExchange exchange) {
        return accounts.authenticate(credentials)
                .flatMap(account -> {
                    if (!account.active()) return invalidCredentials();
                    return exchange.getSession().flatMap(session -> session.changeSessionId()
                            .then(Mono.fromRunnable(() -> session.getAttributes()
                                    .put(GatewaySecurityWebFilter.SESSION_ACCOUNT, account)))
                            .thenReturn(ResponseEntity.ok(ApiResponse.ok(UserView.from(account)))));
                })
                .onErrorResume(AccountClientException.class, error -> {
                    if (error.kind() == AccountClientException.Kind.INVALID_CREDENTIALS) {
                        return invalidCredentials();
                    }
                    return Mono.just(ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                            .header(HttpHeaders.RETRY_AFTER, "1")
                            .body(ApiResponse.fail("账号服务暂时不可用，请稍后重试")));
                });
    }

    @PostMapping("/api/auth/logout")
    public Mono<ApiResponse<String>> logout(ServerWebExchange exchange) {
        return exchange.getSession()
                .flatMap(session -> session.invalidate().thenReturn(ApiResponse.ok("已退出登录")));
    }

    private Mono<ResponseEntity<ApiResponse<UserView>>> invalidCredentials() {
        return Mono.just(ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(ApiResponse.fail("邮箱或密码错误")));
    }

    public record UserView(Long id, String username, String nickname, String phone, String email,
                           String role, String status, String campusRegion, Integer creditScore,
                           LocalDateTime lastActiveAt) {
        static UserView from(AuthenticatedAccount account) {
            return new UserView(account.userId(), account.username(), account.nickname(), account.phone(),
                    account.email(), account.role(), account.status(), account.campusRegion(),
                    account.creditScore(), account.lastActiveAt());
        }
    }
}
