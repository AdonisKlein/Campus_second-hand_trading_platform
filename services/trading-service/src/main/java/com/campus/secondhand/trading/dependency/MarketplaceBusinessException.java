package com.campus.secondhand.trading.dependency;

import com.campus.secondhand.trading.TradingException;
import org.springframework.http.HttpStatus;

/** Business 4xx from Marketplace; ignored by the circuit breaker. */
public final class MarketplaceBusinessException extends RuntimeException {
    private final HttpStatus status;

    public MarketplaceBusinessException(HttpStatus status, String message) {
        super(message);
        this.status = status;
    }

    public TradingException toTradingException() {
        if (status == HttpStatus.NOT_FOUND) {
            return TradingException.notFound(getMessage());
        }
        return new TradingException(status == null ? HttpStatus.BAD_REQUEST : status,
                "MARKETPLACE_REJECTED", "商品信息无效");
    }
}
