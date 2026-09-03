package com.campus.secondhand.trading.dependency;

import com.campus.secondhand.trading.CorrelationIdFilter;
import com.campus.secondhand.trading.InternalWebClients;
import com.campus.secondhand.trading.TradingException;
import com.campus.secondhand.trading.TradingProperties;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import java.time.Duration;
import java.util.Map;
import java.util.function.Supplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;

@Component
public class MarketplaceDependencyClient implements MarketplaceDependency {
    public static final String CIRCUIT_NAME = "marketplaceReads";
    private static final Logger log = LoggerFactory.getLogger(MarketplaceDependencyClient.class);

    private final WebClient client;
    private final TradingProperties properties;
    private final CircuitBreaker circuitBreaker;

    public MarketplaceDependencyClient(WebClient.Builder builder, TradingProperties properties,
                                       CircuitBreakerRegistry registry) {
        this.client = InternalWebClients.create(builder, properties.marketplaceUri(),
                properties.dependencyConnectTimeoutMs());
        this.properties = properties;
        this.circuitBreaker = registry.circuitBreaker(CIRCUIT_NAME);
    }

    @Override
    public Map<String, Object> executeRead(String operation, String path, Object... uriVariables) {
        return execute(operation, true, () -> getOnce(path, uriVariables));
    }

    Map<String, Object> executeOnce(String operation, String path, Object... uriVariables) {
        return execute(operation, false, () -> getOnce(path, uriVariables));
    }

    private Map<String, Object> execute(String operation, boolean retry, Supplier<Map<String, Object>> call) {
        log.info("marketplace read operation={} circuit={} correlationId={}",
                operation, circuitBreaker.getState(), CorrelationIdFilter.current());
        try {
            return invoke(call);
        } catch (RuntimeException first) {
            if (!retry || !isRetryable(first)) {
                throw mapToTrading(first);
            }
            log.info("marketplace retrying safe GET once operation={} circuit={} correlationId={}",
                    operation, circuitBreaker.getState(), CorrelationIdFilter.current());
            try {
                return invoke(call);
            } catch (RuntimeException second) {
                throw mapToTrading(second);
            }
        }
    }

    private Map<String, Object> invoke(Supplier<Map<String, Object>> call) {
        return circuitBreaker.executeSupplier(call);
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> getOnce(String path, Object[] uriVariables) {
        try {
            String correlationId = CorrelationIdFilter.current();
            Map<?, ?> body = client.get().uri(path, uriVariables)
                    .header("X-Internal-Service-Token", properties.internalServiceToken())
                    .header(CorrelationIdFilter.HEADER, correlationId)
                    .retrieve()
                    .onStatus(HttpStatusCode::isError, response -> response.createException())
                    .bodyToMono(Map.class)
                    .timeout(Duration.ofMillis(properties.dependencyResponseTimeoutMs()))
                    .block();
            return body == null ? Map.of() : (Map<String, Object>) body;
        } catch (RuntimeException error) {
            if (httpStatus(error) == 404) {
                return Map.of();
            }
            throw classify(error);
        }
    }

    private RuntimeException classify(Throwable error) {
        WebClientResponseException response = find(error, WebClientResponseException.class);
        if (response != null) {
            HttpStatusCode status = response.getStatusCode();
            if (status.is4xxClientError()) {
                return new MarketplaceBusinessException(HttpStatus.valueOf(status.value()), "商品信息无效");
            }
            return new MarketplaceFailureException("Marketplace HTTP " + status.value(), response);
        }
        return new MarketplaceFailureException("Marketplace unavailable", error);
    }

    private int httpStatus(Throwable error) {
        WebClientResponseException response = find(error, WebClientResponseException.class);
        return response == null ? -1 : response.getStatusCode().value();
    }

    private static <T extends Throwable> T find(Throwable error, Class<T> type) {
        Throwable current = error;
        while (current != null) {
            if (type.isInstance(current)) {
                return type.cast(current);
            }
            current = current.getCause();
        }
        return null;
    }

    private boolean isRetryable(RuntimeException error) {
        return error instanceof MarketplaceFailureException;
    }

    private TradingException mapToTrading(RuntimeException error) {
        if (error instanceof TradingException trading) {
            return trading;
        }
        if (error instanceof MarketplaceBusinessException business) {
            if (business.toTradingException().status() == HttpStatus.NOT_FOUND) {
                return TradingException.notFound("商品不存在");
            }
            return business.toTradingException();
        }
        if (error instanceof CallNotPermittedException || error instanceof MarketplaceFailureException) {
            return TradingException.productUnavailable();
        }
        return TradingException.productUnavailable();
    }

    CircuitBreaker circuitBreaker() {
        return circuitBreaker;
    }
}
