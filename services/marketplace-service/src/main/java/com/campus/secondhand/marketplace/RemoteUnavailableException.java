package com.campus.secondhand.marketplace;
public class RemoteUnavailableException extends MarketplaceException {
    public RemoteUnavailableException(String message, Throwable cause) {
        super(org.springframework.http.HttpStatus.SERVICE_UNAVAILABLE, "SERVICE_UNAVAILABLE", message);
        initCause(cause);
    }
}
