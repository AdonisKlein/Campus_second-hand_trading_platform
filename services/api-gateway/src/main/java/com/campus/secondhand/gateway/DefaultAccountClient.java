package com.campus.secondhand.gateway;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;
import reactor.netty.http.client.HttpClient;
import reactor.util.retry.Retry;

@Component
public class DefaultAccountClient implements AccountClient {
    private final WebClient client;
    private final GatewayProperties properties;

    public DefaultAccountClient(WebClient.Builder builder, GatewayProperties properties) {
        HttpClient http = HttpClient.create()
                .option(io.netty.channel.ChannelOption.CONNECT_TIMEOUT_MILLIS,
                        properties.dependencyConnectTimeoutMs())
                .responseTimeout(Duration.ofMillis(properties.dependencyResponseTimeoutMs()));
        this.client = builder.clientConnector(new ReactorClientHttpConnector(http))
                .baseUrl(properties.accountUri())
                .build();
        this.properties = properties;
    }

    @Override
    public Mono<AuthenticatedAccount> authenticate(LoginCredentials credentials) {
        return client.post()
                .uri("/internal/auth/authenticate")
                .header("X-Internal-Service-Token", properties.internalServiceToken())
                .bodyValue(credentials)
                .retrieve()
                .onStatus(status -> status.value() == HttpStatus.UNAUTHORIZED.value(), response ->
                        Mono.error(new AccountClientException(
                                AccountClientException.Kind.INVALID_CREDENTIALS, null)))
                .onStatus(HttpStatusCode::isError, response -> response.createException().flatMap(error ->
                        Mono.error(new AccountClientException(AccountClientException.Kind.UNAVAILABLE, error))))
                .bodyToMono(Map.class)
                .map(this::unwrap)
                .map(this::authenticatedAccount)
                .onErrorMap(error -> error instanceof AccountClientException ? error
                        : new AccountClientException(AccountClientException.Kind.UNAVAILABLE, error));
    }

    @Override
    public Mono<AccountSecurityState> securityState(Long userId) {
        return client.get()
                .uri(uri -> uri.path("/internal/users/{id}/security-state").build(userId))
                .header("X-Internal-Service-Token", properties.internalServiceToken())
                .retrieve()
                .onStatus(status -> status.value() == HttpStatus.NOT_FOUND.value(), response ->
                        Mono.error(new AccountClientException(AccountClientException.Kind.NOT_FOUND, null)))
                .onStatus(HttpStatusCode::isError, response -> response.createException().flatMap(error ->
                        Mono.error(new AccountClientException(AccountClientException.Kind.UNAVAILABLE, error))))
                .bodyToMono(Map.class)
                .map(this::unwrap)
                .map(this::securityState)
                .retryWhen(Retry.max(1).filter(error -> error instanceof AccountClientException exception
                        && exception.kind() == AccountClientException.Kind.UNAVAILABLE))
                .onErrorMap(error -> error instanceof AccountClientException ? error
                        : new AccountClientException(AccountClientException.Kind.UNAVAILABLE, error));
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> unwrap(Map<?, ?> raw) {
        Object data = raw.get("data");
        if (Boolean.FALSE.equals(raw.get("success")) || !(data instanceof Map<?, ?>)) {
            throw new AccountClientException(AccountClientException.Kind.UNAVAILABLE, null);
        }
        return (Map<String, Object>) data;
    }

    private AuthenticatedAccount authenticatedAccount(Map<String, Object> data) {
        return new AuthenticatedAccount(number(data, "userId"), text(data, "email"), text(data, "username"),
                text(data, "nickname"), text(data, "phone"), text(data, "role"), text(data, "status"),
                text(data, "campusRegion"), integer(data, "creditScore"), dateTime(data, "lastActiveAt"),
                number(data, "authVersion"));
    }

    private AccountSecurityState securityState(Map<String, Object> data) {
        return new AccountSecurityState(number(data, "userId"), text(data, "status"), text(data, "role"),
                number(data, "authVersion"));
    }

    private static String text(Map<String, Object> map, String key) {
        return map.get(key) == null ? null : String.valueOf(map.get(key));
    }

    private static Long number(Map<String, Object> map, String key) {
        Object value = map.get(key);
        if (value instanceof Number number) return number.longValue();
        return Long.valueOf(String.valueOf(value));
    }

    private static Integer integer(Map<String, Object> map, String key) {
        Object value = map.get(key);
        if (value == null) return null;
        if (value instanceof Number number) return number.intValue();
        return Integer.valueOf(String.valueOf(value));
    }

    private static LocalDateTime dateTime(Map<String, Object> map, String key) {
        return map.get(key) == null ? null : LocalDateTime.parse(String.valueOf(map.get(key)));
    }
}
