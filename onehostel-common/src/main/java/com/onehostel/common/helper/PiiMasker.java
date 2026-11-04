package com.onehostel.common.helper;

import java.util.List;
import java.util.Locale;

/**
 * Masks personal data in audit diffs, by field name: phone numbers, emails, ID numbers (Aadhaar,
 * passport, PAN) and bank details keep only a recognisable tail; secrets are removed entirely.
 */
public final class PiiMasker {

    static final String REDACTED = "[REDACTED]";

    private static final List<String> SECRET_FIELDS = List.of("password", "passcode", "secret", "otp", "token", "hash");
    private static final List<String> PII_FIELDS = List.of("mobile", "phone", "email", "aadhaar", "aadhar", "passport",
            "pan", "account", "ifsc", "bank", "upi");

    private PiiMasker() {
    }

    public static Object mask(String fieldPath, Object value) {
        if (value == null) {
            return null;
        }
        String field = leaf(fieldPath).toLowerCase(Locale.ROOT);
        if (SECRET_FIELDS.stream().anyMatch(field::contains)) {
            return REDACTED;
        }
        if (PII_FIELDS.stream().anyMatch(field::contains)) {
            return maskValue(value.toString());
        }
        return value;
    }

    /** "rajesh@onehostel.com" -> "r***@onehostel.com"; "9848012345" -> "******2345". */
    static String maskValue(String value) {
        int at = value.indexOf('@');
        if (at > 0) {
            return value.charAt(0) + "***" + value.substring(at);
        }
        if (value.length() <= 4) {
            return "****";
        }
        return "*".repeat(value.length() - 4) + value.substring(value.length() - 4);
    }

    private static String leaf(String fieldPath) {
        int dot = fieldPath.lastIndexOf('.');
        return dot < 0 ? fieldPath : fieldPath.substring(dot + 1);
    }
}
