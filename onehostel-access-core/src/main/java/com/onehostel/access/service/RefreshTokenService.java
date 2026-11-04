package com.onehostel.access.service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;

import org.springframework.stereotype.Service;

import com.onehostel.access.dto.KeycloakTokenResponse;
import com.onehostel.access.exception.ErrorCode;
import com.onehostel.access.model.RefreshToken;
import com.onehostel.access.model.User;
import com.onehostel.access.repository.RefreshTokenRepository;
import com.onehostel.access.vo.ClientInfo;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Our record of the refresh tokens Keycloak issued: who holds them, which session, and whether they are
 * spent. A refresh is only honoured for a stored, unrevoked, unexpired token.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RefreshTokenService {

    /** Used when Keycloak does not say how long the refresh token lives. */
    private static final Duration DEFAULT_LIFETIME = Duration.ofDays(30);

    private final RefreshTokenRepository repository;

    public void store(KeycloakTokenResponse tokens, User user, ClientInfo client) {
        if (tokens.refreshToken() == null) {
            return;
        }
        RefreshToken token = new RefreshToken();
        token.setTokenHash(hash(tokens.refreshToken()));
        token.setUserId(user.getId());
        token.setKeycloakSessionId(tokens.sessionState());
        token.setExpiresAt(Instant.now().plus(tokens.refreshExpiresIn() != null && tokens.refreshExpiresIn() > 0
                ? Duration.ofSeconds(tokens.refreshExpiresIn())
                : DEFAULT_LIFETIME));
        token.setDeviceId(client.deviceId());
        token.setIpAddress(client.ipAddress());
        token.setUserAgent(client.userAgent());
        repository.save(token);
    }

    /**
     * The stored, live record of a presented token. A token that was already exchanged is a leak:
     * every session of its user is revoked.
     */
    public RefreshToken requireUsable(String rawToken) {
        RefreshToken token = repository.findByTokenHash(hash(rawToken))
                .orElseThrow(ErrorCode.INVALID_REFRESH_TOKEN::raise);
        if (token.isRevoked()) {
            if (token.getReplacedByTokenHash() != null) {
                int revoked = revokeAllExceptSession(token.getUserId(), null);
                log.warn("Refresh token reuse for user {}: {} session(s) revoked", token.getUserId(), revoked);
            }
            throw ErrorCode.INVALID_REFRESH_TOKEN.raise();
        }
        if (token.getExpiresAt().isBefore(Instant.now())) {
            token.setRevoked(true);
            repository.save(token);
            throw ErrorCode.INVALID_REFRESH_TOKEN.raise();
        }
        return token;
    }

    /** Marks a token spent because it was exchanged for the given new one. */
    public void markRotated(RefreshToken token, KeycloakTokenResponse replacement) {
        token.setRevoked(true);
        token.setReplacedByTokenHash(replacement.refreshToken() != null ? hash(replacement.refreshToken()) : null);
        repository.save(token);
    }

    public void revoke(String rawToken) {
        repository.findByTokenHash(hash(rawToken)).filter(token -> !token.isRevoked()).ifPresent(token -> {
            token.setRevoked(true);
            repository.save(token);
        });
    }

    /** Revokes the user's live tokens except those of one Keycloak session (null = all). Returns how many. */
    public int revokeAllExceptSession(String userId, String keepSessionId) {
        List<RefreshToken> live = repository.findByUserIdAndRevokedFalse(userId).stream()
                .filter(token -> keepSessionId == null || !keepSessionId.equals(token.getKeycloakSessionId()))
                .toList();
        live.forEach(token -> token.setRevoked(true));
        repository.saveAll(live);
        return live.size();
    }

    static String hash(String token) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is not available", e);
        }
    }
}
