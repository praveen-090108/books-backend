package com.intelliatech.app.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.intelliatech.app.exception.ApiErrorResponse;
import com.intelliatech.app.web.RequestTraceFilter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class SecurityErrorWriter {
    private final ObjectMapper objectMapper;

    public void write(HttpServletRequest request, HttpServletResponse response, int status,
                      String error, String errorCode, String message, Exception exception) throws IOException {
        String traceId = RequestTraceFilter.current(request);
        log.warn("Security request rejected method={} path={} status={} errorCode={} exception={}",
                request.getMethod(), request.getRequestURI(), status, errorCode,
                exception == null ? "none" : exception.getClass().getSimpleName());
        response.setStatus(status);
        response.setCharacterEncoding("UTF-8");
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setHeader(RequestTraceFilter.HEADER, traceId);
        objectMapper.writeValue(response.getWriter(), ApiErrorResponse.of(
                status, error, errorCode, message, Map.of(), request.getRequestURI(), traceId));
    }
}
