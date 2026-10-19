package com.onehostel.dto.helper;

import java.time.Instant;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.onehostel.exception.ErrorCode;
import com.onehostel.filter.CorrelationIdFilter;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record CommonResponseObject<T>(
        boolean success,
        String message,
        T responseObject,
        int statusCode,
        String errorCode,
        List<FieldError> errors,
        String correlationId,
        Instant timestamp) {

    public record FieldError(String field, String message) {
    }

    public static <T> CommonResponseObject<T> of(int statusCode, String message, T responseObject) {
        return new CommonResponseObject<>(true, message, responseObject, statusCode, null, null,
                CorrelationIdFilter.current(), Instant.now());
    }

    public static <T> CommonResponseObject<T> error(ErrorCode code, String message, List<FieldError> errors) {
        return new CommonResponseObject<>(false, message, null, code.status().value(), code.code(),
                errors == null || errors.isEmpty() ? null : errors, CorrelationIdFilter.current(), Instant.now());
    }
}
