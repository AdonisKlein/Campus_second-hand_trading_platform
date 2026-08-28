package com.campus.secondhand.marketplace;

import java.util.stream.Collectors;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import com.campus.secondhand.marketplace.media.ProductImageException;

@RestControllerAdvice
class MarketplaceExceptionHandler {
    @ExceptionHandler(MarketplaceException.class)
    ResponseEntity<ApiResponse<Void>> marketplace(MarketplaceException error) {
        ResponseEntity.BodyBuilder response = ResponseEntity.status(error.status());
        if (error.status() == HttpStatus.SERVICE_UNAVAILABLE) {
            response.header("Retry-After", "1");
        }
        return response.body(ApiResponse.fail(error.getMessage()));
    }

    @ExceptionHandler(AccessDeniedException.class)
    ResponseEntity<ApiResponse<Void>> forbidden(AccessDeniedException error) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(ApiResponse.fail(error.getMessage()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<ApiResponse<Void>> invalid(MethodArgumentNotValidException error) {
        String message = error.getBindingResult().getFieldErrors().stream()
                .map(value -> value.getField() + ": " + value.getDefaultMessage())
                .collect(Collectors.joining(", "));
        return ResponseEntity.badRequest().body(ApiResponse.fail(message));
    }

    @ExceptionHandler(ProductImageException.class)
    ResponseEntity<ApiResponse<Void>> image(ProductImageException error) {
        return ResponseEntity.status(error.status()).body(ApiResponse.fail(error.getMessage()));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    ResponseEntity<ApiResponse<Void>> invalidArgument(IllegalArgumentException error) {
        return ResponseEntity.badRequest().body(ApiResponse.fail(error.getMessage()));
    }
}
