package com.onehostel.common.exception;

import com.onehostel.common.dto.ApiErrorResponse;

import java.io.IOException;
import java.util.List;

import org.springframework.http.MediaType;

import com.fasterxml.jackson.databind.ObjectMapper;

import jakarta.servlet.http.HttpServletResponse;

/**
 * Writes an ApiErrorResponse straight to the servlet response. For filters and security handlers,
 * which run before DispatcherServlet and so never reach GlobalExceptionHandler.
 */
public class ErrorResponseWriter {

    private final ObjectMapper objectMapper;
    private final String sourceService;

    public ErrorResponseWriter(ObjectMapper objectMapper, String sourceService) {
        this.objectMapper = objectMapper;
        this.sourceService = sourceService;
    }

    public void write(HttpServletResponse response, ErrorCodeSpec code, String message) throws IOException {
        write(response, ApiErrorResponse.of(code, message, List.of(), sourceService));
    }

    public void write(HttpServletResponse response, ApiErrorResponse body) throws IOException {
        if (response.isCommitted()) {
            return;
        }
        response.setStatus(body.statusCode());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        objectMapper.writeValue(response.getOutputStream(), body);
    }

    public String sourceService() {
        return sourceService;
    }
}
