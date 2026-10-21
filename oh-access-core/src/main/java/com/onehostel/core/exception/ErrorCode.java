package com.onehostel.core.exception;

import lombok.Getter;

@Getter
public enum ErrorCode {

    // 400 Bad Request / Validation
    VALIDATION_FAILED("VALIDATION_FAILED", "Request validation failed.", 400),
    MALFORMED_REQUEST("MALFORMED_REQUEST", "Request body is missing or malformed.", 400),
    BAD_REQUEST("BAD_REQUEST", "Bad request syntax or invalid parameters.", 400),
    // 400 rather than 401: the caller is authenticated, only the submitted password is wrong
    INVALID_CURRENT_PASSWORD("INVALID_CURRENT_PASSWORD", "Current password is incorrect.", 400),
    PASSWORD_REUSED("PASSWORD_REUSED", "New password must be different from your current password.", 400),

    // 401 Unauthorized / Authentication Failures
    UNAUTHORIZED("UNAUTHORIZED", "Authentication is required.", 401),
    INVALID_CREDENTIALS("INVALID_CREDENTIALS", "Incorrect login details.", 401),
    INVALID_OTP("INVALID_OTP", "The code is incorrect or has expired.", 401),
    TOKEN_EXPIRED("TOKEN_EXPIRED", "Session token has expired.", 401),
    INVALID_TOKEN("INVALID_TOKEN", "Session token is invalid or malformed.", 401),

    // 403 Forbidden / Authorization & Account State
    FORBIDDEN("FORBIDDEN", "You do not have permission to perform this action.", 403),
    ACCOUNT_DISABLED("ACCOUNT_DISABLED", "This account has been disabled. Contact your administrator.", 403),
    ACCOUNT_NOT_VERIFIED("ACCOUNT_NOT_VERIFIED", "This account has not been verified yet.", 403),
    SECURITY_BREACH("SECURITY_BREACH", "Cross-tenant access violation detected.", 403),
    MOBILE_APP_ONLY("MOBILE_APP_ONLY", "Please log in using the OneHostel Mobile App.", 403),

    // 404 Not Found
    ENTITY_NOT_FOUND("ENTITY_NOT_FOUND", "Requested record was not found.", 404),
    ROUTE_NOT_FOUND("ROUTE_NOT_FOUND", "Requested endpoint does not exist.", 404),

    // 409 Conflict / Duplicates
    DUPLICATE_RECORD("DUPLICATE_RECORD", "Record already exists.", 409),

    // 423 Locked
    ACCOUNT_LOCKED("ACCOUNT_LOCKED", "This account is locked. Contact your administrator.", 423),

    // 429 Rate Limiting
    TOO_MANY_REQUESTS("TOO_MANY_REQUESTS", "Too many attempts. Please wait a moment and try again.", 429),

    // 500 Internal Errors
    INTERNAL_ERROR("INTERNAL_ERROR", "An unexpected error occurred. Please try again or contact support.", 500),

    // 503 Service Unavailable
    SERVICE_UNAVAILABLE("SERVICE_UNAVAILABLE", "Service is temporarily unavailable.", 503);

    private final String code;
    private final String defaultMessage;
    private final int httpStatus;

    ErrorCode(String code, String defaultMessage, int httpStatus) {
        this.code = code;
        this.defaultMessage = defaultMessage;
        this.httpStatus = httpStatus;
    }

    /**
     * Factory helper to throw the exception with the default message.
     */
    public OneHostelException raise() {
        return new OneHostelException(this);
    }

    /**
     * Factory helper to throw the exception with a custom message.
     */
    public OneHostelException raiseWithMessage(String message) {
        return new OneHostelException(this, message);
    }

    /**
     * Factory helper to throw the exception with a root cause.
     */
    public OneHostelException raiseCausedBy(Throwable cause) {
        return new OneHostelException(this, this.defaultMessage, cause);
    }

    /**
     * Factory helper to throw the exception with a custom message and root cause.
     */
    public OneHostelException raiseCausedBy(Throwable cause, String message) {
        return new OneHostelException(this, message, cause);
    }
}