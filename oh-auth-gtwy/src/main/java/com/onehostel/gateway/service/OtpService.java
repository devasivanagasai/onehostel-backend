package com.onehostel.gateway.service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;

import org.springframework.stereotype.Service;

import com.onehostel.core.constants.EnumHelper.ClientSource;
import com.onehostel.core.constants.EnumHelper.OtpChannel;
import com.onehostel.core.constants.EnumHelper.OtpPurpose;
import com.onehostel.core.constants.GlobalConstants;
import com.onehostel.core.dto.CommonResponseObject;
import com.onehostel.core.exception.ErrorCode;
import com.onehostel.core.model.OtpChallengeDoc;
import com.onehostel.core.model.UserDoc;
import com.onehostel.core.security.KeycloakTokenResponse;
import com.onehostel.core.vo.request.OtpSendRequestVo;
import com.onehostel.core.vo.request.OtpVerifyRequestVo;
import com.onehostel.core.vo.response.OtpSendResponseVo;
import com.onehostel.gateway.repository.ReactiveOtpChallengeRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Mono;

@Slf4j
@Service
@RequiredArgsConstructor
public class OtpService {

    private static final SecureRandom RANDOM = new SecureRandom();

    private final ReactiveOtpChallengeRepository otpRepository;
    private final UserLookupService userLookupService;
    private final KeycloakAuthService keycloakAuthService;
    private final KeycloakAdminService keycloakAdminService;
    private final AuthService authService;
    private final OtpDeliveryService otpDeliveryService;

    /** Issues a code for LOGIN or FORGOT_PASSWORD, sent to the email or phone given as identifier. */
    public Mono<CommonResponseObject<OtpSendResponseVo>> send(OtpSendRequestVo request, String ipAddress) {
        String identifier = UserLookupService.normalize(request.getIdentifier());
        OtpPurpose purpose = request.getPurpose() != null ? request.getPurpose() : OtpPurpose.LOGIN;
        Instant windowStart = Instant.now().minus(Duration.ofMinutes(GlobalConstants.MAX_OTP_SENDS_WINDOW_MINUTES));

        return otpRepository.countByIdentifierAndPurposeAndCreatedAtAfter(identifier, purpose, windowStart)
                .flatMap(recentSends -> recentSends >= GlobalConstants.MAX_OTP_ATTEMPTS
                        ? Mono.error(ErrorCode.TOO_MANY_REQUESTS.raise())
                        : userLookupService.findByIdentifier(identifier))
                .switchIfEmpty(Mono.error(ErrorCode.ENTITY_NOT_FOUND.raiseWithMessage("No account found with this identifier")))
                .flatMap(userLookupService::ensureCanSignIn)
                .flatMap(user -> issueChallenge(user, identifier, purpose, ipAddress));
    }

    /** OTP login: validates a LOGIN code and opens a Keycloak session for the user. */
    public Mono<CommonResponseObject<KeycloakTokenResponse>> verifyForLogin(OtpVerifyRequestVo request, boolean acceptAnyCode,
            ClientSource clientSource, String deviceId, String ipAddress, String userAgent) {

        String identifier = UserLookupService.normalize(request.getIdentifier());

        return checkChallenge(identifier, OtpPurpose.LOGIN, request.getOtp(), acceptAnyCode)
                .flatMap(this::consume)
                .then(userLookupService.findByIdentifier(identifier))
                .switchIfEmpty(Mono.error(ErrorCode.INVALID_OTP.raise()))
                .flatMap(userLookupService::ensureCanSignIn)
                .flatMap(user -> userLookupService.ensureAllowedOnClient(user, clientSource))
                .flatMap(user -> requireKeycloakId(user)
                        .flatMap(keycloakAuthService::exchangeForUser)
                                .flatMap(tokens -> authService.persistRefreshToken(tokens, user, deviceId, ipAddress, userAgent)
                                .thenReturn(CommonResponseObject.success("Login successful", tokens, null))));
    }

    /**
     * Finds the newest open challenge for this identifier and purpose and checks the code.
     * A wrong code counts as an attempt; the challenge is not consumed here. With acceptAnyCode
     * (local development only, see DevOtpPolicy) a challenge must still exist but any code matches.
     */
    Mono<OtpChallengeDoc> checkChallenge(String identifier, OtpPurpose purpose, String code, boolean acceptAnyCode) {
        return otpRepository
                .findTopByIdentifierAndPurposeAndIsConsumedFalseAndExpiresAtAfterOrderByCreatedAtDesc(
                        identifier, purpose, Instant.now())
                .switchIfEmpty(Mono.error(ErrorCode.INVALID_OTP.raise()))
                .flatMap(challenge -> {
                    challenge.setUpdatedAt(Instant.now());

                    if (challenge.getAttemptCount() >= GlobalConstants.MAX_OTP_ATTEMPTS) {
                        challenge.setConsumed(true);
                        return otpRepository.save(challenge)
                                .then(Mono.error(ErrorCode.TOO_MANY_REQUESTS.raiseWithMessage(
                                        "Too many wrong codes. Please request a new one.")));
                    }

                    boolean matches = acceptAnyCode || MessageDigest.isEqual(
                            challenge.getOtpHash().getBytes(StandardCharsets.UTF_8),
                            hash(identifier, code).getBytes(StandardCharsets.UTF_8));
                    if (!matches) {
                        challenge.setAttemptCount(challenge.getAttemptCount() + 1);
                        return otpRepository.save(challenge).then(Mono.error(ErrorCode.INVALID_OTP.raise()));
                    }
                    return Mono.just(challenge);
                });
    }

    Mono<OtpChallengeDoc> consume(OtpChallengeDoc challenge) {
        challenge.setConsumed(true);
        challenge.setUpdatedAt(Instant.now());
        return otpRepository.save(challenge);
    }

    /** Keycloak id of the user, looked up by username when the record doesn't store it. */
    Mono<String> requireKeycloakId(UserDoc user) {
        if (user.getKeycloakUserId() != null) {
            return Mono.just(user.getKeycloakUserId());
        }
        return keycloakAdminService.findUserId(UserLookupService.keycloakUsername(user))
                .switchIfEmpty(Mono.defer(() -> {
                    log.error("User {} has no Keycloak account", user.getId());
                    return Mono.error(ErrorCode.INTERNAL_ERROR.raise());
                }));
    }

    private Mono<CommonResponseObject<OtpSendResponseVo>> issueChallenge(
            UserDoc user, String identifier, OtpPurpose purpose, String ipAddress) {

        boolean byPhone = identifier.matches(GlobalConstants.PHONE_REGEX);
        OtpChannel channel = byPhone ? OtpChannel.SMS : OtpChannel.EMAIL;
        String destination = byPhone ? phoneOf(user) : user.getEmail();
        if (destination == null) {
            return Mono.error(ErrorCode.BAD_REQUEST.raiseWithMessage(
                    "This account has no " + (byPhone ? "mobile number" : "email address") + " to send a code to"));
        }

        String code = String.format("%06d", RANDOM.nextInt(1_000_000));
        Instant now = Instant.now();
        OtpChallengeDoc challenge = OtpChallengeDoc.builder()
                .identifier(identifier)
                .otpHash(hash(identifier, code))
                .channel(channel)
                .purpose(purpose)
                .userType(user.getPrimaryAccess() != null ? user.getPrimaryAccess().getRole() : null)
                .isConsumed(false)
                .attemptCount(0)
                .expiresAt(now.plusSeconds(GlobalConstants.OTP_EXPIRY_SECONDS))
                .ipAddress(ipAddress)
                .organizationId(user.getPrimaryAccess() != null ? user.getPrimaryAccess().getOrgId() : null)
                .build();
        challenge.setCreatedAt(now);
        challenge.setUpdatedAt(now);

        OtpSendResponseVo body = OtpSendResponseVo.builder()
                .expiresInSeconds(GlobalConstants.OTP_EXPIRY_SECONDS)
                .channel(channel)
                .devOtp(otpDeliveryService.isDevMode() ? code : null)
                .build();

        return otpRepository.save(challenge)
                .then(otpDeliveryService.deliver(channel, purpose, destination, code, GlobalConstants.OTP_EXPIRY_SECONDS))
                .thenReturn(CommonResponseObject.success("A verification code has been sent", body, null));
    }

    private static String phoneOf(UserDoc user) {
        if (user.getMobileNumber() == null || user.getMobileNumber().getNumber() == null) {
            return null;
        }
        String cc = user.getMobileNumber().getCountryCode();
        return (cc != null ? cc : "") + user.getMobileNumber().getNumber();
    }

    private static String hash(String identifier, String code) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest((identifier + ':' + code).getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 algorithm not available", e);
        }
    }
}
