package com.onehostel.common.exception;

import com.onehostel.common.dto.ApiErrorResponse;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Arrays;
import java.util.stream.Stream;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.http.ResponseEntity;

/** Every catalogue entry produces its declared status and code. Services run the same check on their own ErrorCode. */
class GlobalExceptionHandlerContractTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler("test-service");

    static Stream<ErrorCodeSpec> codes() {
        return Arrays.stream(CommonErrorCode.values());
    }

    @ParameterizedTest
    @MethodSource("codes")
    void everyCodeMapsToItsDeclaredStatus(ErrorCodeSpec code) {
        ResponseEntity<ApiErrorResponse> response = handler.handleHostel(code.raise("Room", "01J"));

        assertThat(response.getStatusCode().value()).isEqualTo(code.status().value());
        ApiErrorResponse body = response.getBody();
        assertThat(body).isNotNull();
        assertThat(body.success()).isFalse();
        assertThat(body.errorCode()).isEqualTo(code.code());
        assertThat(body.statusCode()).isEqualTo(code.status().value());
        assertThat(body.sourceService()).isEqualTo("test-service");
        assertThat(body.message()).doesNotContain("{0}");
    }

    @ParameterizedTest
    @MethodSource("codes")
    void codeIsTheConstantName(CommonErrorCode code) {
        assertThat(code.code()).isEqualTo(code.name());
    }
}
