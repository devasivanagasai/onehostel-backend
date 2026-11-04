package com.onehostel.property.exception;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;

import com.onehostel.common.dto.ApiErrorResponse;
import com.onehostel.common.exception.GlobalExceptionHandler;

/** Every ErrorCode of this service produces its declared status and code. */
class ErrorCodeContractTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler("test");

    @Test
    void everyCodeMapsToItsDeclaredStatus() {
        for (ErrorCode code : ErrorCode.values()) {
            ResponseEntity<ApiErrorResponse> response = handler.handleHostel(code.raise("x", "y", "z"));

            assertThat(response.getStatusCode().value()).as(code.name()).isEqualTo(code.status().value());
            assertThat(response.getBody()).isNotNull();
            assertThat(response.getBody().errorCode()).isEqualTo(code.name());
            assertThat(response.getBody().message()).as(code.name()).doesNotContain("{0}");
        }
    }
}
