package com.onehostel.core.exception;

import java.time.Instant;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonInclude;

/** Stable error response emitted by access-core. */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record ApiErrorResponse(
        String errorCode,
        String message,
        int statusCode,
        Instant timestamp,
        String correlationId,
        String sourceService,
        List<ApiFieldError> fieldErrors) {

    public ApiErrorResponse {
        fieldErrors = fieldErrors == null ? null : List.copyOf(fieldErrors);
    }
}
