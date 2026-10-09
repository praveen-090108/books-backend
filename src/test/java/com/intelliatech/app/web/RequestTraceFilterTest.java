package com.intelliatech.app.web;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class RequestTraceFilterTest {
    private final RequestTraceFilter filter = new RequestTraceFilter();

    @Test
    void preservesSafeIncomingRequestIdAndReturnsIt() throws Exception {
        var request = new MockHttpServletRequest("GET", "/api/customers");
        request.addHeader(RequestTraceFilter.HEADER, "client-request-123");
        var response = new MockHttpServletResponse();

        filter.doFilter(request, response, new MockFilterChain());

        assertThat(response.getHeader(RequestTraceFilter.HEADER)).isEqualTo("client-request-123");
        assertThat(request.getAttribute(RequestTraceFilter.ATTRIBUTE)).isEqualTo("client-request-123");
        assertThat(MDC.get(RequestTraceFilter.MDC_KEY)).isNull();
    }

    @Test
    void replacesUnsafeRequestId() throws Exception {
        var request = new MockHttpServletRequest("GET", "/api/customers");
        request.addHeader(RequestTraceFilter.HEADER, "bad value with spaces");
        var response = new MockHttpServletResponse();

        filter.doFilter(request, response, new MockFilterChain());

        assertThat(response.getHeader(RequestTraceFilter.HEADER)).matches("[a-f0-9]{16}");
    }
}
