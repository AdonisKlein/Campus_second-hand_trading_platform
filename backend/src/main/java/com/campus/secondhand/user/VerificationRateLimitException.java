package com.campus.secondhand.user;

public class VerificationRateLimitException extends RuntimeException {
    public VerificationRateLimitException(String message) { super(message); }
}
