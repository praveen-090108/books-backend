package com.intelliatech.app.exception;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler {
    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<ApiErrorResponse> authentication(AuthenticationException exception) {
        return error(HttpStatus.UNAUTHORIZED, exception.getMessage(), Map.of());
    }

    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<ApiErrorResponse> notFound(ResourceNotFoundException exception) {
        log.warn("API resource was not found: {}", exception.getMessage());
        return error(HttpStatus.NOT_FOUND, exception.getMessage(), null);
    }

    @ExceptionHandler(DuplicateResourceException.class)
    public ResponseEntity<ApiErrorResponse> duplicate(DuplicateResourceException exception) {
        log.warn("API duplicate resource conflict: {}", exception.getMessage());
        return error(HttpStatus.CONFLICT, exception.getMessage(), null);
    }

    @ExceptionHandler(ResourceConflictException.class)
    public ResponseEntity<ApiErrorResponse> conflict(ResourceConflictException exception) {
        log.warn("API operation conflicts with linked data: {}", exception.getMessage());
        return error(HttpStatus.CONFLICT, exception.getMessage(), null);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiErrorResponse> validation(MethodArgumentNotValidException exception) {
        Map<String, String> errors = new LinkedHashMap<>();
        exception.getBindingResult().getFieldErrors()
                .forEach(error -> errors.put(error.getField(), error.getDefaultMessage()));
        log.warn("API request validation failed: {}", errors);
        return error(HttpStatus.BAD_REQUEST, "Validation failed", errors);
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ApiErrorResponse> badRequest(IllegalArgumentException exception) {
        log.warn("API request was rejected: {}", exception.getMessage());
        return error(HttpStatus.BAD_REQUEST, exception.getMessage(), null);
    }

    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<ApiErrorResponse> invalidState(IllegalStateException exception) {
        log.warn("API request is not valid for the current state: {}", exception.getMessage());
        return error(HttpStatus.BAD_REQUEST, exception.getMessage(), null);
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ApiErrorResponse> accessDenied(AccessDeniedException exception) {
        log.warn("API access was denied: {}", exception.getMessage());
        return error(HttpStatus.FORBIDDEN, "You do not have permission to perform this action.", null);
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ApiErrorResponse> argumentTypeMismatch(MethodArgumentTypeMismatchException exception) {
        log.warn("API path or query parameter has an invalid value: {}", exception.getName());
        return error(HttpStatus.BAD_REQUEST, "The value supplied for '" + exception.getName() + "' is invalid.", null);
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ApiErrorResponse> dataIntegrity(DataIntegrityViolationException exception) {
        log.error("Database constraint rejected an API request", exception);
        return error(
                HttpStatus.CONFLICT,
                "This record conflicts with existing data. Refresh the page and try saving again.",
                null
        );
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiErrorResponse> unexpected(Exception exception) {
        log.error("Unexpected API failure", exception);
        return error(HttpStatus.INTERNAL_SERVER_ERROR, "The server could not save the record. Check backend.log for details.", null);
    }

    private ResponseEntity<ApiErrorResponse> error(HttpStatus status, String message, Map<String, String> validationErrors) {
        return ResponseEntity.status(status).body(new ApiErrorResponse(
                LocalDateTime.now(),
                status.value(),
                status.getReasonPhrase(),
                message,
                validationErrors
        ));
    }
}
