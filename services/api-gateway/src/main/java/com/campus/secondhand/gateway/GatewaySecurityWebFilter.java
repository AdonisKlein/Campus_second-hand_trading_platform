package com.campus.secondhand.gateway;

import java.nio.charset.StandardCharsets;
import java.util.Set;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 10)
public class GatewaySecurityWebFilter implements WebFilter {
    static final String SESSION_ACCOUNT = "gateway.account";
    private static final Set<String> PUBLIC_WRITE = Set.of(
            "/api/auth/login",
            "/api/auth/register",
            "/api/auth/verification/register",
            "/api/auth/verification/reset-password",
            "/api/auth/password/reset");
    private static final Set<String> PUBLIC_READ = Set.of(
            "/api/auth/csrf",
            "/actuator/health",
            "/actuator/health/liveness",
            "/actuator/health/readiness",
            "/actuator/info",
            "/api/actuator/health/liveness",
            "/api/actuator/health/readiness",
            "/api/actuator/info",
            "/api/search");

    private final AccountClient accounts;
    private final InternalJwt internalJwt;

    public GatewaySecurityWebFilter(AccountClient accounts, InternalJwt internalJwt) {
        this.accounts = accounts;
        this.internalJwt = internalJwt;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        HttpMethod method = exchange.getRequest().getMethod();
        String path = exchange.getRequest().getPath().value();
        if (method == HttpMethod.OPTIONS) {
            return chain.filter(withoutClientIdentity(exchange));
        }
        if (isUnsafe(method) && PUBLIC_WRITE.contains(path)) {
            return chain.filter(withoutClientIdentity(exchange));
        }
        if (!isUnsafe(method) && isPublicRead(path)) {
            return authenticateIfPresent(exchange, chain);
        }
        return authenticate(exchange, chain);
    }

    private Mono<Void> authenticate(ServerWebExchange exchange, WebFilterChain chain) {
        return authenticate(exchange, chain, true);
    }

    private Mono<Void> authenticateIfPresent(ServerWebExchange exchange, WebFilterChain chain) {
        return authenticate(exchange, chain, false);
    }

    private Mono<Void> authenticate(ServerWebExchange exchange, WebFilterChain chain, boolean required) {
        return exchange.getSession().flatMap(session -> {
            AuthenticatedAccount sessionAccount = session.getAttribute(SESSION_ACCOUNT);
            if (sessionAccount == null) {
                return required
                        ? reject(exchange, HttpStatus.UNAUTHORIZED, "请先登录")
                        : chain.filter(withoutClientIdentity(exchange));
            }
            return accounts.securityState(sessionAccount.userId())
                    .flatMap(current -> {
                        if (!current.matches(sessionAccount)) {
                            return session.invalidate()
                                    .then(reject(exchange, HttpStatus.UNAUTHORIZED, "登录状态已失效"));
                        }
                        ServerWebExchange secured = withoutClientIdentity(exchange).mutate()
                                .request(request -> request.header(HttpHeaders.AUTHORIZATION,
                                        "Bearer " + internalJwt.issue(sessionAccount)))
                                .build();
                        return chain.filter(secured);
                    })
                    .onErrorResume(AccountClientException.class, error -> {
                        if (error.kind() == AccountClientException.Kind.NOT_FOUND) {
                            return session.invalidate()
                                    .then(reject(exchange, HttpStatus.UNAUTHORIZED, "登录状态已失效"));
                        }
                        exchange.getResponse().getHeaders().set(HttpHeaders.RETRY_AFTER, "1");
                        return reject(exchange, HttpStatus.SERVICE_UNAVAILABLE, "账号服务暂时不可用，请稍后重试");
                    });
        });
    }

    private static boolean isPublicRead(String path) {
        if (PUBLIC_READ.contains(path)) return true;
        if (path.startsWith("/api/media/product-images/")) return true;
        if (path.startsWith("/api/messages/item/")) return true;
        if ("/api/items".equals(path)) return true;
        return path.matches("/api/items/[1-9]\\d*");
    }

    private static boolean isUnsafe(HttpMethod method) {
        return method != null && !Set.of(HttpMethod.GET, HttpMethod.HEAD, HttpMethod.OPTIONS).contains(method);
    }

    static ServerWebExchange withoutClientIdentity(ServerWebExchange exchange) {
        return exchange.mutate().request(request -> request.headers(headers -> {
            headers.remove(HttpHeaders.AUTHORIZATION);
            headers.remove("X-User-Id");
            headers.remove("X-User-Role");
            headers.remove("X-Admin-Id");
            headers.remove("X-Internal-Token");
            headers.remove("X-Internal-Service-Token");
        })).build();
    }

    private static Mono<Void> reject(ServerWebExchange exchange, HttpStatus status, String message) {
        exchange.getResponse().setStatusCode(status);
        exchange.getResponse().getHeaders().setContentType(MediaType.APPLICATION_JSON);
        byte[] body = ("{\"success\":false,\"message\":\"" + message + "\",\"data\":null}")
                .getBytes(StandardCharsets.UTF_8);
        return exchange.getResponse().writeWith(Mono.just(exchange.getResponse()
                .bufferFactory().wrap(body)));
    }
}
