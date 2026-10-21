package com.onehostel.core.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.onehostel.core.exception.ErrorCode;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class CommonResponseObject<T> {

    @Builder.Default
    private boolean success = true;

    private String message;

    private T responseObject;

    private int statusCode;

    private String errorCode;

    private List<FieldError> errors;

    private String correlationId;

    @Builder.Default
    private Instant timestamp = Instant.now();

    public static <T> CommonResponseObject<T> success(String message, T data, String correlationId) {
        return CommonResponseObject.<T>builder()
                .success(true)
                .message(message)
                .responseObject(data)
                .statusCode(200)
                .correlationId(correlationId)
                .timestamp(Instant.now())
                .build();
    }

    public static <T> CommonResponseObject<T> error(ErrorCode errorCode, String overrideMessage, List<FieldError> errors, String correlationId) {
        return CommonResponseObject.<T>builder()
                .success(false)
                .message(overrideMessage != null && !overrideMessage.isBlank() ? overrideMessage : errorCode.getDefaultMessage())
                .statusCode(errorCode.getHttpStatus())
                .errorCode(errorCode.getCode())
                .errors(errors != null && !errors.isEmpty() ? errors : null)
                .correlationId(correlationId)
                .timestamp(Instant.now())
                .build();
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class FieldError {
        private String field;
        private String message;
    }
}