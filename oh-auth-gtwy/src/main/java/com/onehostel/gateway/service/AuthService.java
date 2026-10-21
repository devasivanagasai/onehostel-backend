package com.onehostel.gateway.service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.HexFormat;

import org.springframework.stereotype.Service;

import com.onehostel.core.constants.EnumHelper.ClientSource;
import com.onehostel.core.dto.CommonResponseObject;
import com.onehostel.core.exception.ErrorCode;
import com.onehostel.core.model.RefreshTokenDoc;
import com.onehostel.core.model.UserDoc;
import com.onehostel.core.security.KeycloakTokenResponse;
import com.onehostel.core.vo.request.LoginRequestVo;
import com.onehostel.gateway.repository.ReactiveRefreshTokenRepository;
import com.onehostel.gateway.repository.ReactiveUserRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Mono;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuthService {

    private final KeycloakAuthService keycloakAuthService;
    private final ReactiveUserRepository userRepository;
    private final ReactiveRefreshTokenRepository refreshTokenRepository;
    private final UserLookupService userLookupService;

    /**
     * Password login with an email, mobile number or username. The account is resolved in MongoDB
     * first (so a phone number works), then Keycloak checks the password under the account's username.
     */
    public Mono<CommonResponseObject<KeycloakTokenResponse>> login(
            LoginRequestVo request, ClientSource clientSource, String deviceId, String ipAddress, String userAgent) {

        String identifier = UserLookupService.normalize(request.getIdentifier());

        return userLookupService.findByIdentifier(identifier)
                .switchIfEmpty(Mono.error(ErrorCode.INVALID_CREDENTIALS.raise()))
                .flatMap(userLookupService::ensureCanSignIn)
                .flatMap(user -> userLookupService.ensureAllowedOnClient(user, clientSource))
                .flatMap(user -> keycloakAuthService
                        .loginWithPassword(UserLookupService.keycloakUsername(user), request.getPassword())
                        .flatMap(tokens -> persistRefreshToken(tokens, user, deviceId, ipAddress, userAgent)
                                .thenReturn(CommonResponseObject.success("Login successful", tokens, null))));
    }

    /** Revokes every active refresh token of the user (e.g. after a password reset). */
    Mono<Long> revokeAllRefreshTokens(String userId) {
        return revokeRefreshTokensExceptSession(userId, null);
    }

    /** Revokes the user's active refresh tokens except those of the given Keycloak session (null = revoke all). */
    Mono<Long> revokeRefreshTokensExceptSession(String userId, String keepSessionId) {
        return refreshTokenRepository.findAllByUserIdAndIsRevokedFalse(userId)
                .filter(token -> keepSessionId == null || !keepSessionId.equals(token.getKeycloakSessionId()))
                .map(token -> {
                    token.setRevoked(true);
                    token.setUpdatedAt(Instant.now());
                    return token;
                })
                .collectList()
                .flatMap(tokens -> refreshTokenRepository.saveAll(tokens).count());
    }

    public Mono<CommonResponseObject<KeycloakTokenResponse>> refreshToken(
            String refreshToken, String deviceId, String ipAddress, String userAgent) {

        String tokenHash = hashToken(refreshToken);

        return refreshTokenRepository.findByTokenHashAndIsRevokedFalse(tokenHash)
                .switchIfEmpty(Mono.error(ErrorCode.UNAUTHORIZED.raiseWithMessage("Invalid or revoked refresh token")))
                .flatMap(existingTokenDoc -> {
                    if (existingTokenDoc.getExpiresAt().isBefore(Instant.now())) {
                        existingTokenDoc.setRevoked(true);
                        return refreshTokenRepository.save(existingTokenDoc)
                                .then(Mono.error(ErrorCode.UNAUTHORIZED.raiseWithMessage("Refresh token expired")));
                    }

                    return keycloakAuthService.refreshAccessToken(refreshToken)
                            .flatMap(tokenResponse -> {
                                String newRefreshToken = extractRefreshToken(tokenResponse);
                                existingTokenDoc.setRevoked(true);
                                existingTokenDoc.setReplacedByTokenHash(hashToken(newRefreshToken));

                                return refreshTokenRepository.save(existingTokenDoc)
                                        .then(userRepository.findById(existingTokenDoc.getUserId()))
                                        .flatMap(userDoc -> persistRefreshToken(tokenResponse, userDoc, deviceId, ipAddress, userAgent)
                                                .thenReturn(CommonResponseObject.success("Token refreshed successfully", tokenResponse, null))
                                        );
                            });
                });
    }

    public Mono<CommonResponseObject<Void>> logout(String refreshToken) {
        if (refreshToken == null || refreshToken.isBlank()) {
            return Mono.just(CommonResponseObject.success("Logged out successfully", null, null));
        }

        String tokenHash = hashToken(refreshToken);

        return refreshTokenRepository.findByTokenHashAndIsRevokedFalse(tokenHash)
                .flatMap(tokenDoc -> {
                    tokenDoc.setRevoked(true);
                    return refreshTokenRepository.save(tokenDoc);
                })
                .then(keycloakAuthService.revokeToken(refreshToken))
                .thenReturn(CommonResponseObject.success("Logged out successfully", null, null));
    }

    private String extractRefreshToken(KeycloakTokenResponse response) {
        return response != null ? response.getRefreshToken() : null;
    }

    Mono<RefreshTokenDoc> persistRefreshToken(
            KeycloakTokenResponse tokens, UserDoc user, String deviceId, String ipAddress, String userAgent) {

        String rawRefreshToken = extractRefreshToken(tokens);
        if (rawRefreshToken == null) {
            return Mono.empty();
        }

        RefreshTokenDoc doc = RefreshTokenDoc.builder()
                .tokenHash(hashToken(rawRefreshToken))
                .userId(user.getId())
                // Keycloak session id (the access token's "sid"), so one session can be kept while others are revoked
                .keycloakSessionId(tokens.getSessionState())
                .organizationId(user.getPrimaryAccess() != null ? user.getPrimaryAccess().getOrgId() : null)
                .isRevoked(false)
                .expiresAt(Instant.now().plusSeconds(2592000))
                .deviceId(deviceId)
                .ipAddress(ipAddress)
                .userAgent(userAgent)
                .build();

        return refreshTokenRepository.save(doc);
    }

    private String hashToken(String token) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(token.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 algorithm not available", e);
        }
    }
}