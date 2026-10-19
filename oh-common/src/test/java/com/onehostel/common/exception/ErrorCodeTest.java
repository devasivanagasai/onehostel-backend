package com.onehostel.exception;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

class ErrorCodeTest {

    @Test
    void templateArgumentsAreFilledIn() {
        assertThat(ErrorCode.ENTITY_NOT_FOUND.format("Hostel", "h-42")).isEqualTo("Hostel not found: h-42");
        assertThat(ErrorCode.MISSING_REQUIRED_PARAMETER.format("page")).isEqualTo("Required parameter 'page' is missing.");
    }

    @Test
    void passthroughKeepsApostrophes() {
        assertThat(ErrorCode.UPSTREAM_ERROR.format("Couldn't reach gateway")).isEqualTo("Couldn't reach gateway");
    }

    @Test
    void raiseCarriesCodeStatusAndMessage() {
        OneHostelException ex = ErrorCode.INVALID_CREDENTIALS.raise();

        assertThat(ex.getErrorCode().code()).isEqualTo("INVALID_CREDENTIALS");
        assertThat(ex.getErrorCode().status()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(ex.getMessage()).isEqualTo("Incorrect login details.");
    }

    @Test
    void raiseWithMessageKeepsTheWholeSentence() {
        assertThat(ErrorCode.ENTITY_NOT_FOUND.raiseWithMessage("No room is free on that date.").getMessage())
                .isEqualTo("No room is free on that date.");
    }

    @Test
    void propagateWrapsOnlyCheckedExceptions() {
        IllegalStateException unchecked = new IllegalStateException("boom");
        IOException checked = new IOException("disk");

        assertThat(ErrorCode.propagate(unchecked)).isSameAs(unchecked);
        RuntimeException wrapped = ErrorCode.propagate(checked);
        assertThat(wrapped).isInstanceOf(OneHostelException.class).hasCause(checked);
        assertThat(((OneHostelException) wrapped).getErrorCode()).isEqualTo(ErrorCode.INTERNAL_ERROR);
    }

    @Test
    void everyCodeStringMatchesItsConstantName() {
        for (ErrorCode code : ErrorCode.values()) {
            assertThat(code.code()).isEqualTo(code.name());
        }
    }
}
