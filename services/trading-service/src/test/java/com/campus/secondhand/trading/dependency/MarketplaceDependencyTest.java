package com.campus.secondhand.trading.dependency;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.campus.secondhand.trading.CorrelationIdFilter;
import com.campus.secondhand.trading.TradingException;
import com.campus.secondhand.trading.TradingProperties;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import java.io.IOException;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;
import okhttp3.mockwebserver.Dispatcher;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.reactive.function.client.WebClient;

class MarketplaceDependencyTest {
    private MockWebServer marketplace;
    private MarketplaceDependencyClient client;

    @BeforeEach
    void start() throws IOException {
        marketplace = new MockWebServer();
        marketplace.start();
        CircuitBreakerConfig config = CircuitBreakerConfig.custom()
                .slidingWindowType(CircuitBreakerConfig.SlidingWindowType.COUNT_BASED)
                .slidingWindowSize(10)
                .minimumNumberOfCalls(5)
                .failureRateThreshold(50f)
                .waitDurationInOpenState(Duration.ofMillis(250))
                .permittedNumberOfCallsInHalfOpenState(2)
                .automaticTransitionFromOpenToHalfOpenEnabled(true)
                .recordExceptions(MarketplaceFailureException.class)
                .ignoreExceptions(MarketplaceBusinessException.class)
                .build();
        TradingProperties properties = new TradingProperties(
                "http://localhost:1", marketplace.url("/").toString().replaceAll("/$", ""),
                "test-internal-service-token-32-bytes", "jwt", 1440, 4320, false, 200, 150);
        client = new MarketplaceDependencyClient(
                WebClient.builder().filter(CorrelationIdFilter.propagate()),
                properties, CircuitBreakerRegistry.of(config));
    }

    @AfterEach
    void stop() throws IOException {
        marketplace.shutdown();
    }

    @Test
    void timeoutIsMappedToProductUnavailable() {
        marketplace.enqueue(new MockResponse().setBodyDelay(400, java.util.concurrent.TimeUnit.MILLISECONDS)
                .setBody(successBody()).addHeader("Content-Type", "application/json"));
        marketplace.enqueue(new MockResponse().setBodyDelay(400, java.util.concurrent.TimeUnit.MILLISECONDS)
                .setBody(successBody()).addHeader("Content-Type", "application/json"));
        long started = System.nanoTime();
        assertProductUnavailable(() -> client.executeRead("trade-snapshot", "/internal/items/{id}/trade-snapshot", 10));
        assertThat(Duration.ofNanos(System.nanoTime() - started).toMillis()).isLessThan(1_500);
    }

    @Test
    void fiveHundredIsRetriedOnceThenMappedToProductUnavailable() throws InterruptedException {
        marketplace.enqueue(json(500, "{\"success\":false}"));
        marketplace.enqueue(json(500, "{\"success\":false}"));
        assertProductUnavailable(() -> client.executeRead("trade-snapshot", "/internal/items/{id}/trade-snapshot", 10));
        assertThat(marketplace.takeRequest().getPath()).contains("/internal/items/10/trade-snapshot");
        assertThat(marketplace.takeRequest().getPath()).contains("/internal/items/10/trade-snapshot");
        assertThat(marketplace.getRequestCount()).isEqualTo(2);
    }

    @Test
    void notFoundDoesNotCountAsFailureOrTripCircuit() {
        for (int i = 0; i < 8; i++) {
            marketplace.enqueue(json(404, "{\"success\":false,\"message\":\"商品不存在\"}"));
            assertThat(client.executeRead("trade-snapshot", "/internal/items/{id}/trade-snapshot", 99)).isEmpty();
        }
        assertThat(client.circuitBreaker().getState()).isEqualTo(CircuitBreaker.State.CLOSED);
    }

    @Test
    void parameterErrorDoesNotCountAsFailure() {
        for (int i = 0; i < 8; i++) {
            marketplace.enqueue(json(400, "{\"success\":false,\"message\":\"bad\"}"));
            assertThatThrownBy(() -> client.executeRead("trade-snapshot", "/internal/items/{id}/trade-snapshot", 10))
                    .isInstanceOf(TradingException.class)
                    .satisfies(error -> {
                        TradingException trading = (TradingException) error;
                        assertThat(trading.status().is4xxClientError()).isTrue();
                        assertThat(trading.code()).isNotEqualTo("PRODUCT_SERVICE_UNAVAILABLE");
                    });
        }
        assertThat(client.circuitBreaker().getState()).isEqualTo(CircuitBreaker.State.CLOSED);
    }

    @Test
    void nonIdempotentCallsAreNotRetried() {
        marketplace.enqueue(json(500, "{\"success\":false}"));
        assertProductUnavailable(() -> client.executeOnce("trade-snapshot", "/internal/items/{id}/trade-snapshot", 10));
        assertThat(marketplace.getRequestCount()).isEqualTo(1);
    }

    @Test
    void circuitOpensOnRepeatedFailuresAndFailsFast() {
        AtomicInteger status = serveAlways(500);
        for (int i = 0; i < 5; i++) {
            assertProductUnavailable(() -> client.executeRead("trade-snapshot", "/internal/items/{id}/trade-snapshot", 10));
        }
        assertThat(client.circuitBreaker().getState()).isEqualTo(CircuitBreaker.State.OPEN);
        int before = marketplace.getRequestCount();
        long started = System.nanoTime();
        assertProductUnavailable(() -> client.executeRead("trade-snapshot", "/internal/items/{id}/trade-snapshot", 10));
        assertThat(Duration.ofNanos(System.nanoTime() - started).toMillis()).isLessThan(200);
        assertThat(marketplace.getRequestCount()).isEqualTo(before);
        status.set(200);
    }

    @Test
    void circuitRecoversFromHalfOpenToClosedWithoutRestart() throws InterruptedException {
        AtomicInteger status = serveAlways(500);
        for (int i = 0; i < 5; i++) {
            assertProductUnavailable(() -> client.executeRead("trade-snapshot", "/internal/items/{id}/trade-snapshot", 10));
        }
        assertThat(client.circuitBreaker().getState()).isEqualTo(CircuitBreaker.State.OPEN);
        Thread.sleep(300);
        assertThat(client.circuitBreaker().getState()).isEqualTo(CircuitBreaker.State.HALF_OPEN);
        status.set(200);
        assertThat(client.executeRead("trade-snapshot", "/internal/items/{id}/trade-snapshot", 10)).isNotEmpty();
        assertThat(client.executeRead("trade-snapshot", "/internal/items/{id}/trade-snapshot", 10)).isNotEmpty();
        assertThat(client.circuitBreaker().getState()).isEqualTo(CircuitBreaker.State.CLOSED);
    }

    private AtomicInteger serveAlways(int initialStatus) {
        AtomicInteger status = new AtomicInteger(initialStatus);
        marketplace.setDispatcher(new Dispatcher() {
            @Override
            public MockResponse dispatch(RecordedRequest request) {
                int code = status.get();
                return json(code, code == 200 ? successBody() : "{\"success\":false}");
            }
        });
        return status;
    }

    private void assertProductUnavailable(Runnable call) {
        assertThatThrownBy(call::run)
                .isInstanceOf(TradingException.class)
                .satisfies(error -> {
                    TradingException trading = (TradingException) error;
                    assertThat(trading.status()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
                    assertThat(trading.code()).isEqualTo("PRODUCT_SERVICE_UNAVAILABLE");
                    assertThat(trading.getMessage()).isEqualTo("商品服务暂时不可用，请稍后重试");
                });
    }

    private MockResponse json(int status, String body) {
        return new MockResponse().setResponseCode(status).setBody(body).addHeader("Content-Type", "application/json");
    }

    private String successBody() {
        return "{\"success\":true,\"message\":\"success\",\"data\":{\"id\":10,\"sellerId\":7,\"title\":\"教材\","
                + "\"price\":20.00,\"imageUrl\":null,\"status\":\"ON_SALE\",\"moderationStatus\":\"VISIBLE\"}}";
    }
}
