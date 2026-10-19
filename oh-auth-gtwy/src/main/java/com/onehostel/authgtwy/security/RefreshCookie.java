package com.onehostel.authgtwy.security;

import java.time.Duration;

import org.springframework.http.ResponseCookie;

import com.onehostel.common.constants.ServiceConstants;

/**
 * The refresh token cookie: HttpOnly (JavaScript cannot read it), Secure, SameSite=Strict
 * (not sent on cross-site requests, which also blocks CSRF on refresh/logout) and scoped to
 * the auth endpoints. Browsers accept Secure cookies on <a href="http://localhost">...</a>, so dev works too.
 */
public final class RefreshCookie {

    private RefreshCookie() {
    }

    public static ResponseCookie of(String token, Duration maxAge) {
        return base(token).maxAge(maxAge).build();
    }

    public static ResponseCookie cleared() {
        return base("").maxAge(Duration.ZERO).build();
    }

    private static ResponseCookie.ResponseCookieBuilder base(String value) {
        return ResponseCookie.from(ServiceConstants.REFRESH_COOKIE_NAME, value)
                .httpOnly(true)
                .secure(true)
                .sameSite("Strict")
                .path(ServiceConstants.REFRESH_COOKIE_PATH);
    }
}
