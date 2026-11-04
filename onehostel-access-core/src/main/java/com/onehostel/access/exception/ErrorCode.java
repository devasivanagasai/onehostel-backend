package com.onehostel.access.exception;

import org.springframework.http.HttpStatus;

import com.onehostel.common.exception.ErrorCodeSpec;

/**
 * access-core's own error codes; shared ones (VALIDATION_FAILED, ENTITY_NOT_FOUND, ...) are in CommonErrorCode.
 * The Angular app matches on INVALID_OTP, ACCOUNT_LOCKED and MOBILE_APP_ONLY: never rename a code.
 */
public enum ErrorCode implements ErrorCodeSpec {

    INVALID_CREDENTIALS(HttpStatus.UNAUTHORIZED, "Incorrect login details."),
    INVALID_OTP(HttpStatus.UNAUTHORIZED, "The code is incorrect or has expired."),
    INVALID_REFRESH_TOKEN(HttpStatus.UNAUTHORIZED, "Your session has expired. Please log in again."),
    // 400 rather than 401: the caller is signed in, only the submitted password is wrong
    INVALID_CURRENT_PASSWORD(HttpStatus.BAD_REQUEST, "Current password is incorrect."),
    PASSWORD_REUSED(HttpStatus.BAD_REQUEST, "New password must be different from your current password."),
    PASSWORD_POLICY_VIOLATED(HttpStatus.BAD_REQUEST, "The new password does not meet the password policy."),
    NO_OTP_DESTINATION(HttpStatus.BAD_REQUEST, "This account has no {0} to send a code to."),
    UNKNOWN_PRIVILEGE(HttpStatus.BAD_REQUEST, "Unknown or retired privilege: {0}."),
    ROLE_MANAGE_REQUIRED(HttpStatus.BAD_REQUEST, "SUPER_ADMIN must keep {0}, or nobody could manage roles."),
    EMAIL_IN_USE(HttpStatus.CONFLICT, "Another account already uses this email address."),
    MOBILE_IN_USE(HttpStatus.CONFLICT, "Another account already uses this mobile number."),
    PROFILE_REJECTED(HttpStatus.BAD_REQUEST, "{0}"),
    ACCOUNT_DISABLED(HttpStatus.FORBIDDEN, "This account has been disabled. Contact your administrator."),
    ACCOUNT_NOT_VERIFIED(HttpStatus.FORBIDDEN, "This account has not been verified yet."),
    MOBILE_APP_ONLY(HttpStatus.FORBIDDEN, "Please log in using the OneHostel Mobile App."),
    ACCOUNT_LOCKED(HttpStatus.LOCKED, "This account is locked. Contact your administrator."),
    TOO_MANY_CODES(HttpStatus.TOO_MANY_REQUESTS, "Too many codes requested. Please wait a few minutes and try again."),
    TOO_MANY_WRONG_CODES(HttpStatus.TOO_MANY_REQUESTS, "Too many wrong codes. Please request a new one."),
    ACCOUNT_NOT_LINKED(HttpStatus.INTERNAL_SERVER_ERROR, "This account is not set up for sign-in. Contact your administrator."),
    OTP_DELIVERY_UNAVAILABLE(HttpStatus.SERVICE_UNAVAILABLE, "Sending codes is not available right now. Please log in with your password."),
    IDENTITY_PROVIDER_UNAVAILABLE(HttpStatus.SERVICE_UNAVAILABLE, "Sign-in is temporarily unavailable. Please try again shortly.");

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
