package com.campus.secondhand.account;

public class InvalidVerificationCodeException extends RuntimeException {
    public InvalidVerificationCodeException() {
        super("验证码错误、已过期或尝试次数过多");
    }
}
