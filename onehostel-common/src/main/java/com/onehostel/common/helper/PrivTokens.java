package com.onehostel.common.helper;

import java.util.Collection;

/**
 * The privToken: a string of '0'/'1' where position bit-1 is '1' when the privilege with that bit is granted.
 * access-core builds it at sign-in, the gateway checks it per request and the Angular app checks it per page;
 * all three use this one definition of "has the bit".
 */
public final class PrivTokens {

    private PrivTokens() {
    }

    /** A token of {@code length} positions with the given (1-based) bits set. */
    public static String of(Collection<Integer> bits, int length) {
        char[] token = "0".repeat(Math.max(length, 0)).toCharArray();
        for (Integer bit : bits) {
            if (bit != null && bit >= 1 && bit <= token.length) {
                token[bit - 1] = '1';
            }
        }
        return new String(token);
    }

    /** True only when position bit-1 exists and is '1'; a missing token, position or bit is a denial. */
    public static boolean hasBit(String token, int bit) {
        return token != null && bit >= 1 && bit <= token.length() && token.charAt(bit - 1) == '1';
    }

    /** Bitwise OR of several tokens (a user with several roles holds every role's privileges). */
    public static String union(Collection<String> tokens) {
        int length = tokens.stream().mapToInt(t -> t == null ? 0 : t.length()).max().orElse(0);
        char[] merged = "0".repeat(length).toCharArray();
        for (String token : tokens) {
            for (int i = 0; token != null && i < token.length(); i++) {
                if (token.charAt(i) == '1') {
                    merged[i] = '1';
                }
            }
        }
        return new String(merged);
    }
}
