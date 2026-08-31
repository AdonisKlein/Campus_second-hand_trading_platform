package com.campus.secondhand.account;

public class InvalidCredentialsException extends RuntimeException {
    public InvalidCredentialsException() {
        super("邮箱或密码错误");
    }
}
