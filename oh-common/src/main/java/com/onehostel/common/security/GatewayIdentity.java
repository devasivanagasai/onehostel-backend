package com.onehostel.common.security;

import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Optional;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import com.onehostel.common.constants.CommonEnumHelper.Role;

/**
 * How oh-auth-gtwy tells a downstream service who is calling. The gateway checks the JWT, then
 * forwards the caller as plain headers plus an HMAC-SHA256 signature over them, the request
 * method and path, and a timestamp. Downstream services accept the identity only with a valid,
 * fresh signature, so headers sent by anyone other than the gateway are worthless.
 *
 * An anonymous caller (a public endpoint) is forwarded with empty identity fields, still signed,
 * which proves the request came through the gateway.
 */
public final class GatewayIdentity {

    public static final String USER_ID = "X-User-Id";
    public static final String EMAIL = "X-User-Email";
    public static final String ROLE = "X-User-Role";
    public static final String ORGANIZATION_ID = "Organization-Id";
    public static final String HOSTEL_ID = "X-Hostel-Id";
    public static final String BED_ID = "X-Bed-Id";
    public static final String TIMESTAMP = "X-Gateway-Timestamp";
    public static final String SIGNATURE = "X-Gateway-Signature";

    /** Every header the gateway sets; it removes any client-sent copy before forwarding. */
    public static final String[] ALL_HEADERS = {
            USER_ID, EMAIL, ROLE, ORGANIZATION_ID, HOSTEL_ID, BED_ID, TIMESTAMP, SIGNATURE };

    private static final String HMAC = "HmacSHA256";

    private GatewayIdentity() {
    }

    /** The values to forward; {@code user} null means an anonymous (public endpoint) caller. */
    public record Signed(String userId, String email, String role, String organizationId, String hostelId,
            String bedId, long timestamp, String signature) {
    }

    public static Signed sign(byte[] secret, AuthenticatedUser user, String method, String path, Instant now) {
        String userId = user != null ? nz(user.id()) : "";
        String email = user != null ? nz(user.email()) : "";
        String role = user != null && user.role() != null ? user.role().name() : "";
        String organizationId = user != null ? nz(user.organizationId()) : "";
        String hostelId = user != null ? nz(user.hostelId()) : "";
        String bedId = user != null ? nz(user.bedId()) : "";
        long timestamp = now.getEpochSecond();
        String signature = hmac(secret, canonical(userId, email, role, organizationId, hostelId, bedId, timestamp,
                method, path));
        return new Signed(userId, email, role, organizationId, hostelId, bedId, timestamp, signature);
    }

    /**
     * Checks the forwarded headers. Empty when the signature is missing, wrong or older than
     * {@code maxAge}; otherwise the caller, or an empty principal for an anonymous call.
     */
    public static Optional<Verified> verify(byte[] secret, Duration maxAge, String method, String path,
            String userId, String email, String role, String organizationId, String hostelId, String bedId,
            String timestamp, String signature, Instant now) {
        if (signature == null || timestamp == null) {
            return Optional.empty();
        }
        long ts;
        try {
            ts = Long.parseLong(timestamp);
        } catch (NumberFormatException ex) {
            return Optional.empty();
        }
        if (Math.abs(now.getEpochSecond() - ts) > maxAge.getSeconds()) {
            return Optional.empty();
        }
        String expected = hmac(secret, canonical(nz(userId), nz(email), nz(role), nz(organizationId), nz(hostelId),
                nz(bedId), ts, method, path));
        if (!MessageDigest.isEqual(expected.getBytes(StandardCharsets.US_ASCII),
                signature.getBytes(StandardCharsets.US_ASCII))) {
            return Optional.empty();
        }
        if (nz(userId).isEmpty()) {
            return Optional.of(new Verified(null));
        }
        Role parsedRole;
        try {
            parsedRole = Role.valueOf(role);
        } catch (IllegalArgumentException | NullPointerException ex) {
            return Optional.empty();
        }
        return Optional.of(new Verified(new AuthenticatedUser(userId, blankToNull(email), parsedRole,
                blankToNull(organizationId), blankToNull(hostelId), blankToNull(bedId))));
    }

    /** A verified call: {@code user} is null for an anonymous call through a public endpoint. */
    public record Verified(AuthenticatedUser user) {
    }

    private static String canonical(String userId, String email, String role, String organizationId,
            String hostelId, String bedId, long timestamp, String method, String path) {
        return String.join("\n", userId, email, role, organizationId, hostelId, bedId, Long.toString(timestamp),
                method, path);
    }

    private static String hmac(byte[] secret, String data) {
        try {
            Mac mac = Mac.getInstance(HMAC);
            mac.init(new SecretKeySpec(secret, HMAC));
            return HexFormat.of().formatHex(mac.doFinal(data.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException | InvalidKeyException ex) {
            throw new IllegalStateException("Cannot sign gateway identity", ex);
        }
    }

    private static String nz(String value) {
        return value == null ? "" : value;
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }
}
