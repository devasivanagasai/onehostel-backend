package com.onehostel.property.exception;

import org.springframework.http.HttpStatus;

import com.onehostel.common.exception.ErrorCodeSpec;

/**
 * This service's own error codes; shared ones (ENTITY_NOT_FOUND, DUPLICATE_RECORD, ...) are in CommonErrorCode.
 * Each feature adds its domain codes, e.g. {@code BED_ALREADY_OCCUPIED(HttpStatus.CONFLICT, "Bed {0} is already occupied.")}.
 * Never rename a code: add a new one and deprecate the old.
 */
public enum ErrorCode implements ErrorCodeSpec {
    ;

    private final HttpStatus status;
    private final String message;

    ErrorCode(HttpStatus status, String message) {
        this.status = status;
        this.message = message;
    }

    @Override
    public String code() {
        return name();
    }

    @Override
    public HttpStatus status() {
        return status;
    }

    @Override
    public String message() {
        return message;
    }
}
