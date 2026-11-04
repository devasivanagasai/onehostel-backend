package com.onehostel.common.dto;

import com.onehostel.common.helper.CorrelationContext;

import java.time.Instant;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * Success envelope. Controllers return their real type; ResponseEnvelopeAdvice wraps it in this.
 * Errors use ApiErrorResponse, which shares the success / message / statusCode fields.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record CommonResponseObject<T>(
        boolean success,
        String message,
        T responseObject,
        int statusCode,
        String correlationId,
        Instant timestamp) {

    public static <T> CommonResponseObject<T> ok(String message, T responseObject) {
        return new CommonResponseObject<>(true, message, responseObject, 200, CorrelationContext.current(), Instant.now());
    }
}
