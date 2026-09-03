package com.campus.secondhand.trading.dependency;

import com.campus.secondhand.trading.CorrelationIdFilter;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

@Component
class MarketplaceCircuitEvents implements ApplicationRunner {
    private static final Logger log = LoggerFactory.getLogger(MarketplaceCircuitEvents.class);
    private final CircuitBreakerRegistry registry;

    MarketplaceCircuitEvents(CircuitBreakerRegistry registry) {
        this.registry = registry;
    }

    @Override
    public void run(ApplicationArguments args) {
        CircuitBreaker circuit = registry.circuitBreaker(MarketplaceDependencyClient.CIRCUIT_NAME);
        circuit.getEventPublisher().onStateTransition(event -> {
            CircuitBreaker.Metrics metrics = circuit.getMetrics();
            log.info("marketplace circuit transition from={} to={} failureRate={} bufferedCalls={} failedCalls={} correlationId={}",
                    event.getStateTransition().getFromState(),
                    event.getStateTransition().getToState(),
                    metrics.getFailureRate(),
                    metrics.getNumberOfBufferedCalls(),
                    metrics.getNumberOfFailedCalls(),
                    CorrelationIdFilter.current());
        });
    }
}
