package com.campus.secondhand.account;

import java.util.stream.Collectors;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.dao.DataIntegrityViolationException;

@RestControllerAdvice
public class AccountExceptionHandler {
    @ExceptionHandler(MailUnavailableException.class)
    ResponseEntity<ApiResponse<Void>> mailUnavailable() { return response(HttpStatus.SERVICE_UNAVAILABLE, "邮件服务暂不可用"); }

    @ExceptionHandler(VerificationRateLimitException.class)
    ResponseEntity<ApiResponse<Void>> verificationRateLimit() {
        return response(HttpStatus.TOO_MANY_REQUESTS, "请稍后再获取验证码");
    }

    @ExceptionHandler(InvalidVerificationCodeException.class)
    ResponseEntity<ApiResponse<Void>> invalidVerificationCode(InvalidVerificationCodeException ex) {
        return response(HttpStatus.BAD_REQUEST, ex.getMessage());
    }

    @ExceptionHandler(InvalidCredentialsException.class)
    ResponseEntity<ApiResponse<Void>> invalidCredentials(InvalidCredentialsException ex) {
        return response(HttpStatus.UNAUTHORIZED, ex.getMessage());
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    ResponseEntity<ApiResponse<Void>> dataConflict() {
        return response(HttpStatus.CONFLICT, "邮箱或用户名已被使用");
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<ApiResponse<Void>> validation(MethodArgumentNotValidException ex) {
        String message = ex.getBindingResult().getFieldErrors().stream()
                .map(error -> error.getField() + ": " + error.getDefaultMessage()).collect(Collectors.joining(", "));
        return response(HttpStatus.BAD_REQUEST, message);
    }

    @ExceptionHandler(ResponseStatusException.class)
    ResponseEntity<ApiResponse<Void>> status(ResponseStatusException ex) {
        return response(HttpStatus.valueOf(ex.getStatusCode().value()), ex.getReason());
    }

    private ResponseEntity<ApiResponse<Void>> response(HttpStatus status, String message) {
        return ResponseEntity.status(status).body(ApiResponse.fail(message));
    }
}
