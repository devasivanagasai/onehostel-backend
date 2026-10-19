package com.onehostel.constants;

import java.time.Duration;
import java.time.ZoneId;

import org.springframework.data.domain.Sort;

/**
 * Application-wide, domain-agnostic defaults: formats, locale, validation patterns and
 * paging. The regex strings are plain constants so they can be used in
 * {@code @Pattern(regexp = ...)} annotations.
 *
 * Environment-specific values (secrets, URLs, token lifetimes) belong in application.yml /
 * AppProperties, not here.
 */
public final class GlobalConstants {

    // --- Formats and locale ---
    public static final String DATE_FORMAT = "yyyy-MM-dd";
    public static final String DATE_TIME_FORMAT = "yyyy-MM-dd'T'HH:mm:ssXXX"; // ISO-8601 with offset
    public static final String DEFAULT_TIMEZONE = "Asia/Kolkata";
    public static final ZoneId DEFAULT_ZONE = ZoneId.of(DEFAULT_TIMEZONE);
    public static final String DEFAULT_CURRENCY = "INR";
    public static final String DEFAULT_CURRENCY_SYMBOL = "₹";
    /** Mobile numbers are stored as 10 digits; this is their implied country code. */
    public static final String DEFAULT_COUNTRY_CODE = "+91";

    // --- Field limits ---
    /** RFC 5321 maximum for an email address. */
    public static final int EMAIL_MAX_LENGTH = 254;
    public static final int PASSWORD_MIN_LENGTH = 8;
    /** Upper bound so BCrypt (which reads at most 72 bytes) is never fed huge inputs. */
    public static final int PASSWORD_MAX_LENGTH = 128;

    // --- Validation patterns ---
    /** Indian mobile number: 10 digits starting with 6-9, no country code. */
    public static final String PHONE_REGEX = "^[6-9]\\d{9}$";
    /** At least one lower-case letter, upper-case letter, digit and symbol; 8-128 characters. */
    public static final String PASSWORD_REGEX = "^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d)(?=.*[^A-Za-z0-9]).{8,128}$";
    public static final String PAN_REGEX = "^[A-Z]{5}[0-9]{4}[A-Z]$";
    /** 12 digits, never starting with 0 or 1. */
    public static final String AADHAAR_REGEX = "^[2-9]\\d{11}$";
    public static final String GSTIN_REGEX = "^\\d{2}[A-Z]{5}\\d{4}[A-Z][1-9A-Z]Z[0-9A-Z]$";
    /** Indian PIN code: 6 digits, not starting with 0. */
    public static final String PINCODE_REGEX = "^[1-9]\\d{5}$";
    // Emails: use Bean Validation's @Email rather than a home-made regex.

    public static final String PASSCODE_REGEX = "^\\d{6}$";
    public static final String OTP_REGEX = "^\\d{6}$";

    // --- One-time passwords ---
    public static final int OTP_LENGTH = 6;
    public static final Duration OTP_VALIDITY = Duration.ofMinutes(5);
    public static final int OTP_MAX_VERIFY_ATTEMPTS = 5;
    public static final int OTP_MAX_SENDS_PER_WINDOW = 3;
    public static final Duration OTP_SEND_WINDOW = Duration.ofMinutes(15);

    // --- Pagination ---
    public static final int DEFAULT_PAGE = 0;
    public static final int DEFAULT_PAGE_SIZE = 10;
    public static final int MAX_PAGE_SIZE = 100;
    public static final String DEFAULT_SORT_FIELD = "createdDateTime";
    public static final Sort.Direction DEFAULT_SORT_DIRECTION = Sort.Direction.DESC;

    // --- Request envelope ---
    /** Allowed characters for CommonRequestObject.requestId: same rule as the correlation id header. */
    public static final String REQUEST_ID_REGEX = "^[A-Za-z0-9-]{8,64}$";

    private GlobalConstants() {
    }
}
