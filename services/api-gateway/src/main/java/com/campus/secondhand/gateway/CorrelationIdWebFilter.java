package com.campus.secondhand.gateway;

import java.util.UUID;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.ClientRequest;
import org.springframework.web.reactive.function.client.ExchangeFilterFunction;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
final class CorrelationIdWebFilter implements WebFilter {
    static final String HEADER = "X-Correlation-Id";
    static final String CONTEXT_KEY = CorrelationIdWebFilter.class.getName();
    private static final Pattern SAFE = Pattern.compile("[A-Za-z0-9._-]{1,64}");
    private static final Logger log = LoggerFactory.getLogger(CorrelationIdWebFilter.class);

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        String correlationId = normalize(exchange.getRequest().getHeaders().getFirst(HEADER));
        ServerHttpRequest request = exchange.getRequest().mutate().headers(headers -> headers.set(HEADER, correlationId)).build();
        ServerWebExchange correlated = exchange.mutate().request(request).build();
        correlated.getResponse().getHeaders().set(HEADER, correlationId);
        long started = System.nanoTime();
        return chain.filter(correlated)
                .doFinally(signal -> log.info("request completed method={} path={} status={} correlationId={} durationMs={}",
                        request.getMethod(), request.getPath().value(), correlated.getResponse().getStatusCode(),
                        correlationId, (System.nanoTime() - started) / 1_000_000))
                .contextWrite(context -> context.put(CONTEXT_KEY, correlationId));
    }

    static ExchangeFilterFunction propagate() {
        return ExchangeFilterFunction.ofRequestProcessor(request -> Mono.deferContextual(context -> {
            String correlationId = context.getOrDefault(CONTEXT_KEY, UUID.randomUUID().toString());
            return Mono.just(ClientRequest.from(request).headers(headers -> headers.set(HEADER, correlationId)).build());
        }));
    }

    private static String normalize(String value) {
        return value != null && SAFE.matcher(value).matches() ? value : UUID.randomUUID().toString();
    }
}
