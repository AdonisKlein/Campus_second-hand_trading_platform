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

    public HttpStatus status() { return status; }
    public String code() { return code; }
}
