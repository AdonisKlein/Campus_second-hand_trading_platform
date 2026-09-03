package com.campus.secondhand.trading;

import com.campus.secondhand.trading.dependency.MarketplaceDependency;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.Optional;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;

abstract class OwnedRemoteAdapter {
    private final WebClient client;
    private final TradingProperties properties;

    OwnedRemoteAdapter(WebClient.Builder builder, TradingProperties properties, String baseUrl) {
        this.client = InternalWebClients.create(builder, baseUrl, properties.dependencyConnectTimeoutMs());
        this.properties = properties;
    }

    Map<?, ?> get(String path, Object... values) {
        try {
            return client.get().uri(path, values)
                    .header("X-Internal-Service-Token", properties.internalServiceToken())
                    .retrieve().onStatus(HttpStatusCode::isError, response -> response.createException())
                    .bodyToMono(Map.class).timeout(Duration.ofMillis(properties.dependencyResponseTimeoutMs()))
                    .retry(1).block();
        } catch (WebClientResponseException.NotFound ignored) {
            return Map.of();
        } catch (RuntimeException error) {
            throw new TradingException(HttpStatus.SERVICE_UNAVAILABLE, "DEPENDENCY_UNAVAILABLE",
                    "依赖服务暂时不可用");
        }
    }
}

@Component
class HttpAccountAdapter extends OwnedRemoteAdapter implements AccountPort,
        com.campus.secondhand.trading.chat.AccountPort {
    HttpAccountAdapter(WebClient.Builder builder, TradingProperties properties) {
        super(builder, properties, properties.accountUri());
    }

    @Override
    public Optional<AccountSnapshot> find(long userId) {
        Object raw = get("/internal/users/{id}/public", userId).get("data");
        if (!(raw instanceof Map<?, ?> data)) return Optional.empty();
        return Optional.of(new AccountSnapshot(number(data, "id"), text(data, "username"), text(data, "nickname"),
                text(data, "campusRegion"), data.get("creditScore") instanceof Number score ? score.intValue() : null,
                text(data, "status"), text(data, "role"), data.get("lastActiveAt") == null ? null
                : LocalDateTime.parse(text(data, "lastActiveAt"))));
    }

    @Override
    public Optional<com.campus.secondhand.trading.chat.AccountPort.Account> activeStudent(Long id) {
        return find(id).filter(account -> "ACTIVE".equals(account.status()) && "STUDENT".equals(account.role()))
                .map(account -> new com.campus.secondhand.trading.chat.AccountPort.Account(
                        account.id(), account.nickname(), account.username()));
    }

    private long number(Map<?, ?> data, String key) { return ((Number) data.get(key)).longValue(); }
    private String text(Map<?, ?> data, String key) { return data.get(key) == null ? null : String.valueOf(data.get(key)); }
}

@Component
class HttpMarketplaceAdapter implements MarketplacePort,
        com.campus.secondhand.trading.chat.MarketplacePort {
    private final MarketplaceDependency marketplace;

    HttpMarketplaceAdapter(MarketplaceDependency marketplace) {
        this.marketplace = marketplace;
    }

    @Override
    public Optional<ItemSnapshot> find(long itemId) {
        Object raw = marketplace.executeRead("trade-snapshot", "/internal/items/{id}/trade-snapshot", itemId)
                .get("data");
        if (!(raw instanceof Map<?, ?> data)) return Optional.empty();
        return Optional.of(new ItemSnapshot(((Number) data.get("id")).longValue(),
                ((Number) data.get("sellerId")).longValue(), String.valueOf(data.get("title")),
                new java.math.BigDecimal(String.valueOf(data.get("price"))),
                data.get("imageUrl") == null ? null : String.valueOf(data.get("imageUrl")),
                String.valueOf(data.get("status")), String.valueOf(data.get("moderationStatus"))));
    }

    @Override
    public Optional<com.campus.secondhand.trading.chat.MarketplacePort.Item> item(Long id) {
        return find(id).map(item -> new com.campus.secondhand.trading.chat.MarketplacePort.Item(
                item.id(), item.sellerId(), item.title(), item.imageUrl(), item.publiclyTradable()));
    }
}
