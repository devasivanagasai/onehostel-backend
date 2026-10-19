package com.onehostel.service;

import java.util.Locale;

import com.onehostel.constants.GlobalConstants;

/** A login identifier, normalized: emails and usernames lower-cased, phones as 10 digits. */
public record LoginIdentifier(Kind kind, String value) {

    public enum Kind {
        EMAIL,
        PHONE,
        USERNAME
    }

    public static LoginIdentifier parse(String raw) {
        String trimmed = raw.trim();
        if (trimmed.contains("@")) {
            return new LoginIdentifier(Kind.EMAIL, trimmed.toLowerCase(Locale.ROOT));
        }
        if (trimmed.matches(GlobalConstants.PHONE_REGEX)) {
            return new LoginIdentifier(Kind.PHONE, trimmed);
        }
        return new LoginIdentifier(Kind.USERNAME, trimmed.toLowerCase(Locale.ROOT));
    }
}
