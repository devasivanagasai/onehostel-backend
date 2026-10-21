package com.onehostel.core.constants;

public final class GlobalConstants {

    private GlobalConstants() {
        // Prevent instantiation
    }

    // Identifiers & Validation Regex
    public static final String PHONE_REGEX = "^[6-9]\\d{9}$";
    public static final String OTP_REGEX = "^\\d{6}$";
    public static final String PASSCODE_REGEX = "^\\d{6}$";
    public static final String REQUEST_ID_REGEX = "^[A-Za-z0-9-]{8,64}$";
    /** 8-128 chars, at least one lowercase, uppercase, digit and special character, no whitespace. */
    public static final String PASSWORD_REGEX = "^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d)(?=.*[^A-Za-z0-9\\s])\\S{8,128}$";
    public static final String PASSWORD_POLICY_MESSAGE =
            "Password must be 8-128 characters with an uppercase letter, a lowercase letter, a digit and a special character, and no spaces";
    public static final String DEFAULT_COUNTRY_CODE = "+91";

    // Pagination Defaults
    public static final int DEFAULT_PAGE_NUMBER = 0;
    public static final int DEFAULT_PAGE_SIZE = 10;
    public static final int MAX_PAGE_SIZE = 100;

    // Timeouts & Expirations (in seconds)
    public static final int OTP_EXPIRY_SECONDS = 300; // 5 minutes
    public static final int MAX_OTP_ATTEMPTS = 5;
    public static final int MAX_OTP_SENDS_WINDOW_MINUTES = 15;
}