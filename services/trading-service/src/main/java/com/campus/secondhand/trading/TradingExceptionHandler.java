package com.campus.secondhand.trading;

import java.util.stream.Collectors;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
class TradingExceptionHandler {
    @ExceptionHandler(TradingException.class)
    ResponseEntity<ApiResponse<Void>> trading(TradingException error) {
        var builder = ResponseEntity.status(error.status());
        if (error.status() == HttpStatus.SERVICE_UNAVAILABLE) builder.header("Retry-After", "1");
        return builder.body(ApiResponse.fail(error.code(), error.getMessage()));
    }

    @ExceptionHandler(AccessDeniedException.class)
    ResponseEntity<ApiResponse<Void>> forbidden() {
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(ApiResponse.fail("没有权限执行此操作"));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<ApiResponse<Void>> invalid(MethodArgumentNotValidException error) {
        String message = error.getBindingResult().getFieldErrors().stream()
                .map(field -> field.getField() + ": " + field.getDefaultMessage())
                .collect(Collectors.joining(", "));
        return ResponseEntity.badRequest().body(ApiResponse.fail(message));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    ResponseEntity<ApiResponse<Void>> invalidArgument(IllegalArgumentException error) {
        return ResponseEntity.badRequest().body(ApiResponse.fail(error.getMessage()));
    }
}
