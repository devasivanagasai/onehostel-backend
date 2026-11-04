package com.onehostel.common.exception;

import org.springframework.http.HttpStatus;

/**
 * Codes every service shares. Domain codes (BED_ALREADY_OCCUPIED, INVALID_OTP, ...) live in each
 * service's own ErrorCode enum. The frontend matches on these strings: never rename one.
 */
public enum CommonErrorCode implements ErrorCodeSpec {

    VALIDATION_FAILED(HttpStatus.BAD_REQUEST, "Request validation failed."),
    MALFORMED_REQUEST(HttpStatus.BAD_REQUEST, "Request body is missing or malformed."),
    BAD_REQUEST(HttpStatus.BAD_REQUEST, "{0}"),
    UNAUTHORIZED(HttpStatus.UNAUTHORIZED, "Authentication is required."),
    FORBIDDEN(HttpStatus.FORBIDDEN, "You do not have permission to perform this action."),
    SECURITY_BREACH(HttpStatus.FORBIDDEN, "You do not have access to this record."),
    ENTITY_NOT_FOUND(HttpStatus.NOT_FOUND, "{0} not found: {1}"),
    ROUTE_NOT_FOUND(HttpStatus.NOT_FOUND, "Requested endpoint does not exist."),
    METHOD_NOT_ALLOWED(HttpStatus.METHOD_NOT_ALLOWED, "This endpoint does not support {0}."),
    UNSUPPORTED_MEDIA_TYPE(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "Content type {0} is not supported."),
    DUPLICATE_RECORD(HttpStatus.CONFLICT, "{0} already exists."),
    OPTIMISTIC_LOCK_FAILED(HttpStatus.CONFLICT, "Record was updated by another request. Reload and retry."),
    STATE_CONFLICT(HttpStatus.CONFLICT, "{0}"),
    TOO_MANY_REQUESTS(HttpStatus.TOO_MANY_REQUESTS, "Too many attempts. Please wait a moment and try again."),
    INTERNAL_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "An unexpected error occurred. Please try again or contact support."),
    SERVICE_UNAVAILABLE(HttpStatus.SERVICE_UNAVAILABLE, "Service is temporarily unavailable.");

    private final HttpStatus status;
    private final String message;

    CommonErrorCode(HttpStatus status, String message) {
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
