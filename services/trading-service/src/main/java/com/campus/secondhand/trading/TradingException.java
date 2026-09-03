package com.campus.secondhand.trading;

import org.springframework.http.HttpStatus;

public class TradingException extends RuntimeException {
    private final HttpStatus status;
    private final String code;

    public TradingException(HttpStatus status, String code, String message) {
        super(message);
        this.status = status;
        this.code = code;
    }

    public static TradingException forbidden(String message) {
        return new TradingException(HttpStatus.FORBIDDEN, "FORBIDDEN", message);
    }

    public static TradingException notFound(String message) {
        return new TradingException(HttpStatus.NOT_FOUND, "NOT_FOUND", message);
    }

    public static TradingException conflict(String code, String message) {
        return new TradingException(HttpStatus.CONFLICT, code, message);
    }

    public static TradingException productUnavailable() {
        return new TradingException(HttpStatus.SERVICE_UNAVAILABLE, "PRODUCT_SERVICE_UNAVAILABLE",
                "商品服务暂时不可用，请稍后重试");
    }

    public HttpStatus status() { return status; }
    public String code() { return code; }
}
