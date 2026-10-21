package com.onehostel.core.constants;

public final class ServiceConstants {

    private ServiceConstants() {
        // Prevent instantiation
    }

    // Downstream Injected Headers
    public static final String CORRELATION_ID_HEADER = "X-Correlation-Id";
    public static final String USER_ID_HEADER = "X-User-Id";
    public static final String USER_ROLE_HEADER = "X-User-Role";
    public static final String ORGANIZATION_ID_HEADER = "X-Organization-Id";
    public static final String HOSTEL_ID_HEADER = "X-Hostel-Id";
    public static final String BED_ID_HEADER = "X-Bed-Id";
    public static final String USER_EMAIL_HEADER = "X-User-Email";

    // Auth & Cookie Keys
    public static final String REFRESH_COOKIE_NAME = "oh_refresh";
    public static final String BEARER_PREFIX = "Bearer ";
    public static final String MDC_CORRELATION_ID_KEY = "correlationId";

    // Public Path Patterns
    public static final String[] AUTH_PUBLIC_PATHS = {
            "/api/v1/auth/login",
            "/api/v1/auth/otp/send",
            "/api/v1/auth/refresh-token",
            "/api/v1/auth/logout",
            "/actuator/health"
    };
}