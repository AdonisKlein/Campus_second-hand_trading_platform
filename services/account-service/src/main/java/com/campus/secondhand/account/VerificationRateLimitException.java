package com.campus.secondhand.account;

public class VerificationRateLimitException extends RuntimeException {
    public VerificationRateLimitException() {
        super("请稍后再获取验证码");
    }
}
