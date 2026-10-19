package com.onehostel.constants;

import java.time.Duration;
import java.util.Set;

/**
 * API routes, HTTP header names, network limits and module identifiers. Route constants are
 * compile-time strings so they work in {@code @RequestMapping} and security matchers.
 */
public final class ServiceConstants {

    // --- API routes ---
    public static final String API_BASE = "/api/v1";

    public static final String AUTH_BASE = API_BASE + "/auth";
    public static final String AUTH_LOGIN = "/login";
    public static final String AUTH_OTP_SEND = "/otp/send";
    public static final String AUTH_REFRESH = "/refresh-token";
    public static final String AUTH_LOGOUT = "/logout";
    public static final String AUTH_ME = "/me";

    /** Callable without an access token. Logout and refresh authenticate with the refresh cookie. */
    public static final String[] AUTH_PUBLIC_PATHS = {
            AUTH_BASE + AUTH_LOGIN, AUTH_BASE + AUTH_OTP_SEND, AUTH_BASE + AUTH_REFRESH, AUTH_BASE + AUTH_LOGOUT };

    /** Per-IP rate limited (AuthRateLimitFilter): every public auth path except logout. */
    public static final Set<String> AUTH_RATE_LIMITED_PATHS = Set.of(
            AUTH_BASE + AUTH_LOGIN, AUTH_BASE + AUTH_OTP_SEND, AUTH_BASE + AUTH_REFRESH);

    /** HttpOnly cookie holding the refresh token, sent only to the auth endpoints. */
    public static final String REFRESH_COOKIE_NAME = "oh_refresh";
    public static final String REFRESH_COOKIE_PATH = AUTH_BASE;

    /** Role areas, each restricted to one role in SecurityConfig. */
    public static final String ADMIN_BASE = API_BASE + "/admin";
    public static final String OWNER_BASE = API_BASE + "/owner";
    public static final String TENANT_BASE = API_BASE + "/tenant";

    // Feature modules. Each exposes the uniform actions below (HostelCoreController).
    public static final String HOSTEL_BASE = API_BASE + "/hostels";

    public static final String ACTION_CREATE = "/create";
    public static final String ACTION_UPDATE = "/update";
    public static final String ACTION_INQUIRE = "/inq";
    public static final String ACTION_GET_ALL = "/getall";
    public static final String ACTION_DELETE = "/delete";

    public static final String HEALTH_PATH = "/actuator/health";
    public static final String[] SWAGGER_PATHS = { "/v3/api-docs/**", "/swagger-ui.html", "/swagger-ui/**" };

    // --- HTTP headers ---
    // Standard ones (Authorization, Content-Type) come from Spring's HttpHeaders.
    public static final String CORRELATION_ID_HEADER = "X-Correlation-Id";
    public static final String BEARER_PREFIX = "Bearer ";

    // --- Logging ---
    /** MDC key under which CorrelationIdFilter stores the request's correlation id. */
    public static final String CORRELATION_ID_MDC_KEY = "correlationId";

    // --- Outbound calls (for future HTTP clients: payment gateway, notifications) ---
    public static final Duration HTTP_CONNECT_TIMEOUT = Duration.ofSeconds(5);
    public static final Duration HTTP_READ_TIMEOUT = Duration.ofSeconds(15);
    public static final int HTTP_MAX_RETRIES = 3;

    // --- Module identifiers, for logs and audit entries ---
    public static final String AUTH_SERVICE = "AUTH_SERVICE";
    public static final String PAYMENT_SERVICE = "PAYMENT_SERVICE";
    public static final String NOTIFICATION_SERVICE = "NOTIFICATION_SERVICE";

    private ServiceConstants() {
    }
}
