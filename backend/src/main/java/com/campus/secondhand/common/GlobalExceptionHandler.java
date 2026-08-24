package com.campus.secondhand.common;

import jakarta.validation.ConstraintViolationException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.http.converter.HttpMessageNotReadableException;

import java.util.stream.Collectors;
import com.campus.secondhand.user.VerificationRateLimitException;
import org.springframework.security.access.AccessDeniedException;
import com.campus.secondhand.order.TradingRuleException;
import com.campus.secondhand.item.SellerInventoryRuleException;
import com.campus.secondhand.media.ProductImageException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import com.campus.secondhand.search.SearchQueryException;
import com.campus.secondhand.report.GovernanceRuleException;
import org.springframework.dao.DataIntegrityViolationException;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<Object>> handleValidationError(MethodArgumentNotValidException ex) {
        String msg = ex.getBindingResult().getFieldErrors().stream()
            .map(e -> e.getField() + ": " + e.getDefaultMessage())
            .collect(Collectors.joining("; "));
        ApiResponse<Object> body = ApiResponse.fail(msg);
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        return new ResponseEntity<>(body, headers, HttpStatus.BAD_REQUEST);
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ApiResponse<Object>> handleConstraintViolation(ConstraintViolationException ex) {
        String msg = ex.getConstraintViolations().stream()
            .map(v -> v.getPropertyPath() + ": " + v.getMessage())
            .collect(Collectors.joining("; "));
        ApiResponse<Object> body = ApiResponse.fail(msg);
        return ResponseEntity.badRequest().contentType(MediaType.APPLICATION_JSON).body(body);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiResponse<Object>> handleBadJson(HttpMessageNotReadableException ex, WebRequest request) {
        ApiResponse<Object> body = ApiResponse.fail("请求体格式错误，请检查 JSON 格式和必填字段");
        return ResponseEntity.badRequest().contentType(MediaType.APPLICATION_JSON).body(body);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Object>> handleOther(Exception ex) {
        ApiResponse<Object> body = ApiResponse.fail("服务器内部错误");
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).contentType(MediaType.APPLICATION_JSON).body(body);
    }

    @ExceptionHandler(VerificationRateLimitException.class)
    public ResponseEntity<ApiResponse<Object>> handleRateLimit(VerificationRateLimitException ex) {
        return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
            .contentType(MediaType.APPLICATION_JSON).body(ApiResponse.fail(ex.getMessage()));
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ApiResponse<Object>> handleAccessDenied(AccessDeniedException ex) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
            .contentType(MediaType.APPLICATION_JSON).body(ApiResponse.fail("无权执行此操作"));
    }

    @ExceptionHandler(TradingRuleException.class)
    public ResponseEntity<ApiResponse<Object>> handleTradingRule(TradingRuleException ex) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
            .contentType(MediaType.APPLICATION_JSON).body(ApiResponse.fail(ex.getMessage()));
    }

    @ExceptionHandler(SellerInventoryRuleException.class)
    public ResponseEntity<ApiResponse<Object>> handleSellerInventoryRule(SellerInventoryRuleException ex) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
            .contentType(MediaType.APPLICATION_JSON).body(ApiResponse.fail(ex.getMessage()));
    }

    @ExceptionHandler(ProductImageException.class)
    public ResponseEntity<ApiResponse<Object>> handleProductImage(ProductImageException ex) {
        return ResponseEntity.status(ex.status())
            .contentType(MediaType.APPLICATION_JSON).body(ApiResponse.fail(ex.getMessage()));
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<ApiResponse<Object>> handleMaxUploadSize(MaxUploadSizeExceededException ex) {
        return ResponseEntity.status(HttpStatus.PAYLOAD_TOO_LARGE)
            .contentType(MediaType.APPLICATION_JSON).body(ApiResponse.fail("图片不能超过 5MB"));
    }

    @ExceptionHandler(SearchQueryException.class)
    public ResponseEntity<ApiResponse<Object>> handleSearchQuery(SearchQueryException ex) {
        return ResponseEntity.badRequest().contentType(MediaType.APPLICATION_JSON).body(ApiResponse.fail(ex.getMessage()));
    }

    @ExceptionHandler(GovernanceRuleException.class)
    public ResponseEntity<ApiResponse<Void>> handleGovernanceRule(GovernanceRuleException ex) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(ApiResponse.fail(ex.getMessage()));
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ApiResponse<Void>> handleDataConflict(DataIntegrityViolationException ex) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(ApiResponse.fail("数据冲突，请刷新后重试"));
    }
}

