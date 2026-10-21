package com.onehostel.core.util;

import com.onehostel.core.constants.GlobalConstants;

public record LoginIdentifier(Type type, String value) {

    public enum Type {
        EMAIL,
        PHONE,
        USERNAME
    }

    public static LoginIdentifier parse(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new IllegalArgumentException("Identifier cannot be null or blank");
        }

        String trimmed = raw.trim();

        if (trimmed.contains("@")) {
            return new LoginIdentifier(Type.EMAIL, trimmed.toLowerCase());
        }

        if (trimmed.matches(GlobalConstants.PHONE_REGEX)) {
            return new LoginIdentifier(Type.PHONE, trimmed);
        }

        return new LoginIdentifier(Type.USERNAME, trimmed.toLowerCase());
    }
}