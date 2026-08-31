package com.campus.secondhand.gateway;

class AccountClientException extends RuntimeException {
    enum Kind { INVALID_CREDENTIALS, NOT_FOUND, UNAVAILABLE }

    private final Kind kind;

    AccountClientException(Kind kind, Throwable cause) {
        super(kind.name(), cause);
        this.kind = kind;
    }

    Kind kind() {
        return kind;
    }
}
