package com.intelliatech.app.exception;

import static org.assertj.core.api.Assertions.assertThat;

import com.intelliatech.app.web.RequestTraceFilter;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockHttpServletRequest;

class GlobalExceptionHandlerTest {
    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @Test
    void unexpectedFailureIsSafeAndTraceable() {
        var request = request();
        var response = handler.unexpected(new RuntimeException("database password secret"), request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().success()).isFalse();
        assertThat(response.getBody().errorCode()).isEqualTo("INTERNAL_SERVER_ERROR");
        assertThat(response.getBody().message()).doesNotContain("password", "secret");
        assertThat(response.getBody().traceId()).isEqualTo("trace-test-123");
        assertThat(response.getHeaders().getFirst(RequestTraceFilter.HEADER)).isEqualTo("trace-test-123");
    }

    @Test
    void duplicateDatabaseFailureDoesNotExposeSqlDetails() {
        var response = handler.dataIntegrity(
                new DataIntegrityViolationException("duplicate key: customer_code_idx secret sql"), request());

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(response.getBody().errorCode()).isEqualTo("DUPLICATE_RECORD");
        assertThat(response.getBody().message()).doesNotContain("customer_code_idx", "sql");
    }

    private MockHttpServletRequest request() {
        var request = new MockHttpServletRequest("POST", "/api/test");
        request.setAttribute(RequestTraceFilter.ATTRIBUTE, "trace-test-123");
        return request;
    }
}
