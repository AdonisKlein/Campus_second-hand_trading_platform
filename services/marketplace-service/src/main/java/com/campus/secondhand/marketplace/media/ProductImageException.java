package com.campus.secondhand.marketplace.media;
import org.springframework.http.HttpStatus;
public class ProductImageException extends RuntimeException {
    private final HttpStatus status;
    public ProductImageException(HttpStatus status, String message) { super(message); this.status = status; }
    public HttpStatus status() { return status; }
}
