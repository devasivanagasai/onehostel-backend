package com.onehostel.constants;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Generic helpers for any enum: list its values, parse/validate raw strings from requests,
 * and turn constants into display labels or dropdown options.
 */
public final class EnumHelper {

    /** One dropdown entry: what the user sees and what the client sends back. */
    public record EnumOption(String label, String value) {
    }

    private EnumHelper() {
    }

    public static <E extends Enum<E>> List<E> values(Class<E> type) {
        return List.of(type.getEnumConstants());
    }

    public static <E extends Enum<E>> List<String> names(Class<E> type) {
        return Arrays.stream(type.getEnumConstants()).map(Enum::name).toList();
    }

    /**
     * The constant named by {@code raw}, ignoring case and surrounding spaces, or empty
     * when it is null, blank or not a member.
     */
    public static <E extends Enum<E>> Optional<E> parse(Class<E> type, String raw) {
        if (raw == null || raw.isBlank()) {
            return Optional.empty();
        }
        String name = raw.trim().toUpperCase(Locale.ROOT);
        return Arrays.stream(type.getEnumConstants()).filter(e -> e.name().equals(name)).findFirst();
    }

    public static <E extends Enum<E>> boolean isValid(Class<E> type, String raw) {
        return parse(type, raw).isPresent();
    }

    /** {@code HOSTEL_OWNER} becomes {@code "Hostel Owner"}. */
    public static String toLabel(Enum<?> value) {
        return Arrays.stream(value.name().split("_"))
                .filter(word -> !word.isEmpty())
                .map(word -> word.charAt(0) + word.substring(1).toLowerCase(Locale.ROOT))
                .collect(Collectors.joining(" "));
    }

    public static <E extends Enum<E>> List<EnumOption> options(Class<E> type) {
        return Arrays.stream(type.getEnumConstants())
                .map(e -> new EnumOption(toLabel(e), e.name()))
                .toList();
    }
}
