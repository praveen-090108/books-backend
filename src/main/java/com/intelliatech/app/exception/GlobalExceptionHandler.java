package com.intelliatech.app.exception;

import com.intelliatech.app.web.RequestTraceFilter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import java.util.LinkedHashMap;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.validation.BindException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.client.RestClientException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.MultipartException;

@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler {
    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<ApiErrorResponse> authentication(AuthenticationException exception, HttpServletRequest request) {
        return handled(HttpStatus.UNAUTHORIZED, "AUTHENTICATION_REQUIRED",
                "Authentication is required or your session has expired.", Map.of(), exception, request);
    }

    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<ApiErrorResponse> notFound(ResourceNotFoundException exception, HttpServletRequest request) {
        return handled(HttpStatus.NOT_FOUND, "RESOURCE_NOT_FOUND", exception.getMessage(), Map.of(), exception, request);
    }

    @ExceptionHandler(DuplicateResourceException.class)
    public ResponseEntity<ApiErrorResponse> duplicate(DuplicateResourceException exception, HttpServletRequest request) {
        return handled(HttpStatus.CONFLICT, "DUPLICATE_RECORD", exception.getMessage(), Map.of(), exception, request);
    }

    @ExceptionHandler(ResourceConflictException.class)
    public ResponseEntity<ApiErrorResponse> conflict(ResourceConflictException exception, HttpServletRequest request) {
        return handled(HttpStatus.CONFLICT, "RESOURCE_CONFLICT", exception.getMessage(), Map.of(), exception, request);
    }

    @ExceptionHandler({MethodArgumentNotValidException.class, BindException.class})
    public ResponseEntity<ApiErrorResponse> validation(Exception exception, HttpServletRequest request) {
        var binding = exception instanceof MethodArgumentNotValidException method
                ? method.getBindingResult() : ((BindException) exception).getBindingResult();
        Map<String, String> errors = new LinkedHashMap<>();
        binding.getFieldErrors().forEach(error -> errors.putIfAbsent(error.getField(), error.getDefaultMessage()));
        binding.getGlobalErrors().forEach(error -> errors.putIfAbsent("_form", error.getDefaultMessage()));
        return handled(HttpStatus.UNPROCESSABLE_ENTITY, "VALIDATION_ERROR",
                "Please check the highlighted fields.", errors, exception, request);
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ApiErrorResponse> constraintValidation(ConstraintViolationException exception,
                                                                  HttpServletRequest request) {
        Map<String, String> errors = new LinkedHashMap<>();
        exception.getConstraintViolations().forEach(violation ->
                errors.putIfAbsent(violation.getPropertyPath().toString(), violation.getMessage()));
        return handled(HttpStatus.UNPROCESSABLE_ENTITY, "VALIDATION_ERROR",
                "Please check the highlighted fields.", errors, exception, request);
    }

    @ExceptionHandler({MissingServletRequestParameterException.class, MissingRequestHeaderException.class,
            MethodArgumentTypeMismatchException.class, HttpMessageNotReadableException.class})
    public ResponseEntity<ApiErrorResponse> malformedRequest(Exception exception, HttpServletRequest request) {
        String message = exception instanceof MethodArgumentTypeMismatchException mismatch
                ? "The value supplied for '" + mismatch.getName() + "' is invalid."
                : "The request is incomplete or contains an invalid value.";
        return handled(HttpStatus.BAD_REQUEST, "INVALID_REQUEST", message, Map.of(), exception, request);
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ApiErrorResponse> badRequest(IllegalArgumentException exception, HttpServletRequest request) {
        return handled(HttpStatus.BAD_REQUEST, "BUSINESS_VALIDATION_ERROR",
                safeMessage(exception.getMessage(), "The request could not be completed."), Map.of(), exception, request);
    }

    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<ApiErrorResponse> invalidState(IllegalStateException exception, HttpServletRequest request) {
        return handled(HttpStatus.CONFLICT, "INVALID_OPERATION",
                safeMessage(exception.getMessage(), "This operation is not available in the record's current state."),
                Map.of(), exception, request);
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ApiErrorResponse> accessDenied(AccessDeniedException exception, HttpServletRequest request) {
        return handled(HttpStatus.FORBIDDEN, "ACCESS_DENIED",
                "You don't have permission to perform this action.", Map.of(), exception, request);
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ApiErrorResponse> dataIntegrity(DataIntegrityViolationException exception,
                                                           HttpServletRequest request) {
        String root = rootMessage(exception).toLowerCase();
        String code = root.contains("duplicate") || root.contains("unique") ? "DUPLICATE_RECORD" : "DATA_CONFLICT";
        String message = "DUPLICATE_RECORD".equals(code)
                ? "A record with the same unique value already exists. Please review the information and try again."
                : "This record conflicts with existing or linked data. Refresh the page and try again.";
        return failed(HttpStatus.CONFLICT, code, message, exception, request);
    }

    @ExceptionHandler(DataAccessResourceFailureException.class)
    public ResponseEntity<ApiErrorResponse> databaseUnavailable(Exception exception, HttpServletRequest request) {
        return failed(HttpStatus.SERVICE_UNAVAILABLE, "DATABASE_UNAVAILABLE",
                "The data service is temporarily unavailable. Please try again shortly.", exception, request);
    }

    @ExceptionHandler({MaxUploadSizeExceededException.class, MultipartException.class})
    public ResponseEntity<ApiErrorResponse> upload(Exception exception, HttpServletRequest request) {
        String message = exception instanceof MaxUploadSizeExceededException
                ? "The selected file exceeds the allowed size."
                : "The selected file could not be uploaded. Please verify the file and try again.";
        return handled(HttpStatus.BAD_REQUEST, "FILE_UPLOAD_ERROR", message, Map.of(), exception, request);
    }

    @ExceptionHandler(RestClientException.class)
    public ResponseEntity<ApiErrorResponse> externalService(Exception exception, HttpServletRequest request) {
        return failed(HttpStatus.BAD_GATEWAY, "EXTERNAL_SERVICE_ERROR",
                "An external service could not complete the request. Please try again shortly.", exception, request);
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ApiErrorResponse> methodNotAllowed(HttpRequestMethodNotSupportedException exception,
                                                              HttpServletRequest request) {
        return handled(HttpStatus.METHOD_NOT_ALLOWED, "METHOD_NOT_ALLOWED",
                "This operation is not supported for the requested endpoint.", Map.of(), exception, request);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiErrorResponse> unexpected(Exception exception, HttpServletRequest request) {
        return failed(HttpStatus.INTERNAL_SERVER_ERROR, "INTERNAL_SERVER_ERROR",
                "We couldn't complete your request. Please try again or contact support.", exception, request);
    }

    private ResponseEntity<ApiErrorResponse> handled(HttpStatus status, String errorCode, String message,
                                                       Map<String, String> fields, Exception exception,
                                                       HttpServletRequest request) {
        log.warn("API request rejected method={} path={} status={} errorCode={} exception={} rootCause={}",
                request.getMethod(), request.getRequestURI(), status.value(), errorCode,
                exception.getClass().getSimpleName(), rootSummary(exception));
        return response(status, errorCode, message, fields, request);
    }

    private ResponseEntity<ApiErrorResponse> failed(HttpStatus status, String errorCode, String message,
                                                      Exception exception, HttpServletRequest request) {
        log.error("API request failed method={} path={} status={} errorCode={} exception={} rootCause={}",
                request.getMethod(), request.getRequestURI(), status.value(), errorCode,
                exception.getClass().getSimpleName(), rootSummary(exception), exception);
        return response(status, errorCode, message, Map.of(), request);
    }

    private ResponseEntity<ApiErrorResponse> response(HttpStatus status, String errorCode, String message,
                                                       Map<String, String> fields, HttpServletRequest request) {
        String traceId = RequestTraceFilter.current(request);
        return ResponseEntity.status(status)
                .header(RequestTraceFilter.HEADER, traceId)
                .body(ApiErrorResponse.of(status.value(), status.getReasonPhrase(), errorCode,
                        message, fields, request.getRequestURI(), traceId));
    }

    private String rootSummary(Throwable throwable) {
        Throwable root = root(throwable);
        String message = root.getMessage();
        return root.getClass().getSimpleName() + (message == null ? "" : ": " + message);
    }

    private String rootMessage(Throwable throwable) {
        String message = root(throwable).getMessage();
        return message == null ? "" : message;
    }

    private Throwable root(Throwable throwable) {
        Throwable current = throwable;
        while (current.getCause() != null && current.getCause() != current) current = current.getCause();
        return current;
    }

    private String safeMessage(String value, String fallback) {
        if (value == null || value.isBlank()) return fallback;
        String lower = value.toLowerCase();
        if (lower.contains("sqlexception") || lower.contains("hibernate") || lower.contains("jdbc:")
                || lower.contains("select ") || lower.contains("insert ") || lower.contains("update ")
                || lower.contains("delete from") || value.length() > 500) return fallback;
        return value;
    }
}
