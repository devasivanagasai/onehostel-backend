package com.onehostel.common.dto;

import java.time.Instant;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.onehostel.common.exception.ErrorCodeSpec;
import com.onehostel.common.helper.CorrelationContext;

/**
 * Body of every error response. The HTTP status always equals statusCode. Field names match what the
 * Angular app reads (CommonResponseObject in auth.models.ts): success, message, statusCode, errorCode, errors.
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record ApiErrorResponse(
        boolean success,
        String errorCode,
        String message,
        int statusCode,
        List<ApiFieldError> errors,
        String correlationId,
        String sourceService,
        Instant timestamp) {

    public static ApiErrorResponse of(ErrorCodeSpec code, String message, List<ApiFieldError> errors, String sourceService) {
        return new ApiErrorResponse(false, code.code(), message, code.status().value(), errors,
                CorrelationContext.current(), sourceService, Instant.now());
    }

    public static ApiErrorResponse of(ErrorCodeSpec code, String sourceService) {
        return of(code, code.message(), List.of(), sourceService);
    }
}
