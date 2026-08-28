package com.campus.secondhand.marketplace;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.Optional;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import reactor.netty.http.client.HttpClient;
import io.netty.channel.ChannelOption;

abstract class RemoteClient {
    final WebClient client; final MarketplaceProperties props;
    RemoteClient(WebClient.Builder b, MarketplaceProperties p, String base) {
        this.client = b.clone()
                .clientConnector(new ReactorClientHttpConnector(HttpClient.create()
                        .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, 300)))
                .baseUrl(base).build();
        this.props = p;
    }
    Map<?,?> get(String path, Object... args) {
        try { return client.get().uri(path, args).header("X-Internal-Service-Token", props.internalServiceToken())
            .retrieve().onStatus(HttpStatusCode::isError, r -> r.createException()).bodyToMono(Map.class)
            .timeout(Duration.ofMillis(800)).retry(1).block(); }
        catch (WebClientResponseException.NotFound ignored) { return java.util.Collections.emptyMap(); }
        catch (Exception e) { throw new RemoteUnavailableException("下游服务暂时不可用", e); }
    }
}

@Component
class HttpAccountPublicAdapter extends RemoteClient implements AccountPublicPort {
    HttpAccountPublicAdapter(WebClient.Builder b, MarketplaceProperties p) { super(b,p,p.accountUri()); }
    public Optional<PublicAccount> findPublic(long id) {
        Map<?,?> d = (Map<?,?>) get("/internal/users/{id}/public", id).get("data");
        if (d == null) return Optional.empty();
        return Optional.of(new PublicAccount(((Number)d.get("id")).longValue(), String.valueOf(d.get("username")),
            text(d,"nickname"), text(d,"campusRegion"),
            d.get("creditScore") == null ? null : ((Number)d.get("creditScore")).intValue(), String.valueOf(d.get("status")),
            String.valueOf(d.get("role")), d.get("lastActiveAt")==null?null:LocalDateTime.parse(String.valueOf(d.get("lastActiveAt")))));
    }
    private String text(Map<?,?> data,String key){return data.get(key)==null?null:String.valueOf(data.get(key));}
}

@Component
class HttpTradingInquiryAdapter extends RemoteClient implements TradingInquiryPort {
    HttpTradingInquiryAdapter(WebClient.Builder b, MarketplaceProperties p) { super(b,p,p.tradingUri()); }
    public Optional<Inquiry> activeInquiry(long itemId, long buyerId) {
        Map<?,?> d = (Map<?,?>) get("/internal/trading/items/{itemId}/buyers/{buyerId}/active-inquiry", itemId, buyerId).get("data");
        if (d == null) return Optional.empty();
        return Optional.of(new Inquiry(((Number)d.get("id")).longValue(), String.valueOf(d.get("status")), String.valueOf(d.get("expiresAt"))));
    }
}
