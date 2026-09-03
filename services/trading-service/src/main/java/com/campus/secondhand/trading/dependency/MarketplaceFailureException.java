package com.campus.secondhand.trading.dependency;

/** Network, timeout and Marketplace 5xx failures recorded by the circuit breaker. */
public final class MarketplaceFailureException extends RuntimeException {
    public MarketplaceFailureException(String message, Throwable cause) {
        super(message, cause);
    }

    public MarketplaceFailureException(String message) {
        super(message);
    }
}
