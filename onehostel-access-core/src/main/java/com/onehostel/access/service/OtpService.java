package com.onehostel.access.service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.HexFormat;

import org.springframework.stereotype.Service;

import com.onehostel.access.config.OtpProperties;
import com.onehostel.access.constants.AccessEnums.OtpChannel;
import com.onehostel.access.constants.AccessEnums.OtpPurpose;
import com.onehostel.access.exception.ErrorCode;
import com.onehostel.access.helper.OtpDelivery;
import com.onehostel.access.model.OtpChallenge;
import com.onehostel.access.model.User;
import com.onehostel.access.repository.OtpChallengeRepository;
import com.onehostel.access.vo.ClientInfo;
import com.onehostel.common.exception.CommonErrorCode;

import lombok.RequiredArgsConstructor;

/**
 * Issues and checks one-time codes. A code is valid for app.otp.expiry, allows app.otp.max-attempts wrong
 * tries, and each identifier gets at most app.otp.max-sends-per-window codes per window.
 */
@Service
@RequiredArgsConstructor
public class OtpService {

    private static final SecureRandom RANDOM = new SecureRandom();

    private final OtpChallengeRepository repository;
    private final UserLookupService userLookup;
    private final OtpDelivery delivery;
    private final OtpProperties properties;

    /** The issued challenge and, in dev mode only, the code itself. */
    public record Issued(OtpChannel channel, long expiresInSeconds, String devCode) {
    }

    /**
     * Sends a code for LOGIN or FORGOT_PASSWORD to the email or mobile number given as identifier.
     * An unknown account is reported as 404 ENTITY_NOT_FOUND: the sign-in screen shows it on the field.
     */
    public Issued send(String rawIdentifier, OtpPurpose purpose, ClientInfo client) {
        String identifier = UserLookupService.normalize(rawIdentifier);
        Instant windowStart = Instant.now().minus(properties.sendWindow());
        if (repository.countByIdentifierAndPurposeAndCreatedAtAfter(identifier, purpose, windowStart) >= properties.maxSendsPerWindow()) {
            throw ErrorCode.TOO_MANY_CODES.raise();
        }
        User user = userLookup.findByIdentifier(identifier)
                .orElseThrow(() -> CommonErrorCode.ENTITY_NOT_FOUND.raiseWithMessage("No account found with this identifier"));
        userLookup.ensureCanSignIn(user);

        boolean byPhone = UserLookupService.isPhone(identifier);
        OtpChannel channel = byPhone ? OtpChannel.SMS : OtpChannel.EMAIL;
        String destination = byPhone
                ? (user.getMobileNumber() != null ? user.getMobileNumber().international() : null)
                : user.getEmail();
        if (destination == null) {
            throw ErrorCode.NO_OTP_DESTINATION.raise(byPhone ? "mobile number" : "email address");
        }

        String code = String.format("%06d", RANDOM.nextInt(1_000_000));
        OtpChallenge challenge = new OtpChallenge();
        challenge.setIdentifier(identifier);
        challenge.setCodeHash(hash(identifier, code));
        challenge.setPurpose(purpose);
        challenge.setChannel(channel);
        challenge.setExpiresAt(Instant.now().plus(properties.expiry()));
        challenge.setIpAddress(client.ipAddress());
        repository.save(challenge);

        delivery.deliver(channel, purpose, destination, code);
        return new Issued(channel, properties.expiry().toSeconds(), delivery.isDevMode() ? code : null);
    }

    /**
     * The newest open challenge for this identifier and purpose, if the code matches. A wrong code counts
     * as an attempt; the challenge is not consumed here. With acceptAnyCode (local development, see
     * DevOtpPolicy) a challenge must still exist, but any code matches.
     */
    public OtpChallenge check(String identifier, OtpPurpose purpose, String code, boolean acceptAnyCode) {
        OtpChallenge challenge = repository
                .findFirstByIdentifierAndPurposeAndConsumedFalseAndExpiresAtAfterOrderByCreatedAtDesc(identifier, purpose, Instant.now())
                .orElseThrow(ErrorCode.INVALID_OTP::raise);

        if (challenge.getAttemptCount() >= properties.maxAttempts()) {
            challenge.setConsumed(true);
            repository.save(challenge);
            throw ErrorCode.TOO_MANY_WRONG_CODES.raise();
        }
        boolean matches = acceptAnyCode || MessageDigest.isEqual(
                challenge.getCodeHash().getBytes(StandardCharsets.UTF_8),
                hash(identifier, code).getBytes(StandardCharsets.UTF_8));
        if (!matches) {
            challenge.setAttemptCount(challenge.getAttemptCount() + 1);
            repository.save(challenge);
            throw ErrorCode.INVALID_OTP.raise();
        }
        return challenge;
    }

    public void consume(OtpChallenge challenge) {
        challenge.setConsumed(true);
        repository.save(challenge);
    }

    static String hash(String identifier, String code) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest((identifier + ':' + code).getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is not available", e);
        }
    }
}
