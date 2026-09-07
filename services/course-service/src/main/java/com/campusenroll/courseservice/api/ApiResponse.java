package com.campusenroll.courseservice.api;

import java.time.Instant;

public record ApiResponse<T>(
        int code,
        String message,
        T data,
        String requestId,
        long timestamp) {

    public static <T> ApiResponse<T> success(T data, String requestId) {
        return new ApiResponse<>(0, "success", data, requestId, Instant.now().toEpochMilli());
    }

    public static ApiResponse<Void> error(int code, String message, String requestId) {
        return new ApiResponse<>(code, message, null, requestId, Instant.now().toEpochMilli());
    }
}
