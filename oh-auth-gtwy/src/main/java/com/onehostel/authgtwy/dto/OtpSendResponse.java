package com.onehostel.dto.auth;

/** Same answer whether or not an account matched, so it cannot be used to find accounts. */
public record OtpSendResponse(long expiresInSeconds) {
}
