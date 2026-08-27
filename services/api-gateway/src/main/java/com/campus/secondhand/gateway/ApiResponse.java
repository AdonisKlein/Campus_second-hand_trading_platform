package com.campus.secondhand.gateway;

public record ApiResponse<T>(boolean success, String message, T data) {
    static <T> ApiResponse<T> ok(T data) {
        return new ApiResponse<>(true, "success", data);
    }

    static <T> ApiResponse<T> fail(String message) {
        return new ApiResponse<>(false, message, null);
    }
}
