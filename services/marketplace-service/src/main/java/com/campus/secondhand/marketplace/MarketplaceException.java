package com.campus.secondhand.marketplace;

import org.springframework.http.HttpStatus;

public class MarketplaceException extends RuntimeException {
    private final HttpStatus status;
    private final String code;

    public MarketplaceException(HttpStatus status, String code, String message) {
        super(message);
        this.status = status;
        this.code = code;
    }

    public MarketplaceException(String code, String message) {
        this(HttpStatus.CONFLICT, code, message);
    }

    public HttpStatus status() { return status; }
    public String code() { return code; }
}
