package com.intelliatech.app.exception;

import java.time.Instant;
import java.util.Map;

public record ApiErrorResponse(
        boolean success,
        int status,
        String error,
        String errorCode,
        String message,
        Map<String, String> fieldErrors,
        Map<String, String> validationErrors,
        String path,
        Instant timestamp,
        String traceId
) {
    public static ApiErrorResponse of(int status, String error, String errorCode, String message,
                                      Map<String, String> fieldErrors, String path, String traceId) {
        Map<String, String> safeFields = fieldErrors == null ? Map.of() : Map.copyOf(fieldErrors);
        return new ApiErrorResponse(false, status, error, errorCode, message, safeFields, safeFields,
                path, Instant.now(), traceId);
    }
}
