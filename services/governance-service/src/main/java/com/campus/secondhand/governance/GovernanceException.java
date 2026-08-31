package com.campus.secondhand.governance;

import org.springframework.http.HttpStatus;

class GovernanceException extends RuntimeException {
    private final HttpStatus status;
    private final String code;

    GovernanceException(HttpStatus status, String code, String message) {
        super(message); this.status = status; this.code = code;
    }
    HttpStatus status() { return status; }
    String code() { return code; }
    static GovernanceException forbidden(String message) { return new GovernanceException(HttpStatus.FORBIDDEN, "FORBIDDEN", message); }
    static GovernanceException notFound(String message) { return new GovernanceException(HttpStatus.NOT_FOUND, "NOT_FOUND", message); }
    static GovernanceException conflict(String code, String message) { return new GovernanceException(HttpStatus.CONFLICT, code, message); }
    static GovernanceException unavailable() { return new GovernanceException(HttpStatus.SERVICE_UNAVAILABLE, "SERVICE_UNAVAILABLE", "依赖服务暂时不可用"); }
}
