package com.campus.secondhand.governance;

import java.util.stream.Collectors;
import org.springframework.http.*;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.*;

@RestControllerAdvice
class GovernanceExceptionHandler {
    @ExceptionHandler(GovernanceException.class)
    ResponseEntity<ApiResponse<Void>> governance(GovernanceException error) {
        ResponseEntity.BodyBuilder response = ResponseEntity.status(error.status());
        if (error.status() == HttpStatus.SERVICE_UNAVAILABLE) response.header("Retry-After", "1");
        return response.body(ApiResponse.fail(error.getMessage()));
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
