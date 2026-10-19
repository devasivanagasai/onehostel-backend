package com.onehostel.exception;

import java.text.MessageFormat;

import org.springframework.http.HttpStatus;

public enum ErrorCode {

    VALIDATION_FAILED("VALIDATION_FAILED", HttpStatus.BAD_REQUEST, "Request validation failed."),

    MALFORMED_REQUEST("MALFORMED_REQUEST", HttpStatus.BAD_REQUEST, "Request body is missing or malformed."),

    BAD_REQUEST("BAD_REQUEST", HttpStatus.BAD_REQUEST, Template.PASSTHROUGH),

    MISSING_REQUIRED_PARAMETER("MISSING_REQUIRED_PARAMETER", HttpStatus.BAD_REQUEST,
            "Required parameter ''{0}'' is missing."),

    METHOD_NOT_ALLOWED("METHOD_NOT_ALLOWED", HttpStatus.METHOD_NOT_ALLOWED,
            "HTTP method not supported for this endpoint."),


    UNAUTHORIZED("UNAUTHORIZED", HttpStatus.UNAUTHORIZED, "Authentication is required."),

    INVALID_CREDENTIALS("INVALID_CREDENTIALS", HttpStatus.UNAUTHORIZED, "Incorrect login details."),

    /** Wrong, expired, already used or over-attempted: one message, so none of these is revealed. */
    INVALID_OTP("INVALID_OTP", HttpStatus.UNAUTHORIZED, "The code is incorrect or has expired."),

    TOO_MANY_REQUESTS("TOO_MANY_REQUESTS", HttpStatus.TOO_MANY_REQUESTS,
            "Too many attempts. Please wait a moment and try again."),

    ACCOUNT_DISABLED("ACCOUNT_DISABLED", HttpStatus.FORBIDDEN,
            "This account has been disabled. Contact your administrator."),

    ACCOUNT_LOCKED("ACCOUNT_LOCKED", HttpStatus.LOCKED,
            "This account is locked. Contact your administrator."),

    ACCOUNT_NOT_VERIFIED("ACCOUNT_NOT_VERIFIED", HttpStatus.FORBIDDEN,
            "This account has not been verified yet."),

    FORBIDDEN("FORBIDDEN", HttpStatus.FORBIDDEN, "You do not have permission to perform this action."),

    NOT_FOUND("NOT_FOUND", HttpStatus.NOT_FOUND, "The requested resource was not found."),

    ENTITY_NOT_FOUND("ENTITY_NOT_FOUND", HttpStatus.NOT_FOUND, "{0} not found: {1}"),

    DUPLICATE_RECORD("DUPLICATE_RECORD", HttpStatus.CONFLICT, "{0} already exists."),

    RECORD_IN_USE("RECORD_IN_USE", HttpStatus.CONFLICT, Template.PASSTHROUGH),

    STATE_CONFLICT("STATE_CONFLICT", HttpStatus.CONFLICT, Template.PASSTHROUGH),

    OPTIMISTIC_LOCK_FAILED("OPTIMISTIC_LOCK_FAILED", HttpStatus.CONFLICT,
            "This record was updated by someone else. Please reload and try again."),

    UPSTREAM_ERROR("UPSTREAM_ERROR", HttpStatus.BAD_GATEWAY, Template.PASSTHROUGH),

    UPSTREAM_UNAVAILABLE("UPSTREAM_UNAVAILABLE", HttpStatus.SERVICE_UNAVAILABLE, Template.PASSTHROUGH),

    INTERNAL_ERROR("INTERNAL_ERROR", HttpStatus.INTERNAL_SERVER_ERROR,
            "An unexpected error occurred. Please try again or contact support.");

    private static final class Template {

        private static final String PASSTHROUGH = "{0}";

        private Template() {
        }
    }

    private final String code;
    private final HttpStatus status;
    private final String messageTemplate;

    ErrorCode(String code, HttpStatus status, String messageTemplate) {
        this.code = code;
        this.status = status;
        this.messageTemplate = messageTemplate;
    }

    public String code() {
        return code;
    }

    public HttpStatus status() {
        return status;
    }

    public String format(Object... args) {
        if (args == null || args.length == 0) {
            return Template.PASSTHROUGH.equals(messageTemplate) ? "" : messageTemplate;
        }
        if (Template.PASSTHROUGH.equals(messageTemplate) && args.length == 1) {
            return String.valueOf(args[0]);
        }
        return MessageFormat.format(messageTemplate, args);
    }

    public OneHostelException raise(Object... args) {
        return new OneHostelException(this, format(args), null);
    }

    public OneHostelException raiseCausedBy(Throwable cause, Object... args) {
        return new OneHostelException(this, format(args), cause);
    }

    public OneHostelException raiseWithMessage(String message) {
        return new OneHostelException(this, message, null);
    }

    public static RuntimeException propagate(Exception exception) {
        return exception instanceof RuntimeException runtimeException
                ? runtimeException
                : INTERNAL_ERROR.raiseCausedBy(exception);
    }
}
