package com.onehostel.service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.UUID;

import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Service;

import com.onehostel.config.AppProperties;
import com.onehostel.exception.ErrorCode;
import com.onehostel.model.RefreshToken;

import lombok.extern.slf4j.Slf4j;

/** Issues, rotates and revokes refresh tokens. See RefreshToken for the reuse-detection rule. */
@Slf4j
@Service
public class RefreshTokenService {

    private static final int TOKEN_BYTES = 32;

    private final MongoTemplate mongoTemplate;
    private final Duration ttl;
    private final SecureRandom random = new SecureRandom();

    public RefreshTokenService(MongoTemplate mongoTemplate, AppProperties properties) {
        this.mongoTemplate = mongoTemplate;
        this.ttl = properties.jwt().refreshExpiration();
    }

    public record Rotation(String userId, String familyId) {
    }

    public Duration ttl() {
        return ttl;
    }

    /** A new token for a fresh login ({@code familyId} null) or the next one in a rotation. */
    public String issue(String userId, String familyId) {
        byte[] bytes = new byte[TOKEN_BYTES];
        random.nextBytes(bytes);
        String raw = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);

        Instant now = Instant.now();
        RefreshToken token = new RefreshToken();
        token.setTokenHash(hash(raw));
        token.setUserId(userId);
        token.setFamilyId(familyId != null ? familyId : UUID.randomUUID().toString());
        token.setCreatedAt(now);
        token.setExpiresAt(now.plus(ttl));
        mongoTemplate.insert(token);
        return raw;
    }

    /** Revokes the presented token and says whose it was. Any problem answers UNAUTHORIZED. */
    public Rotation consume(String raw) {
        RefreshToken token = find(raw);
        if (token == null) {
            throw ErrorCode.UNAUTHORIZED.raise();
        }
        if (token.getRevokedAt() != null) {
            log.warn("Revoked refresh token reused for user {}; revoking its session family", token.getUserId());
            revokeFamily(token.getFamilyId());
            throw ErrorCode.UNAUTHORIZED.raise();
        }
        if (token.getExpiresAt().isBefore(Instant.now())) {
            throw ErrorCode.UNAUTHORIZED.raise();
        }
        // Conditional, so two concurrent refreshes with the same token cannot both succeed.
        long claimed = mongoTemplate.updateFirst(
                new Query(Criteria.where("id").is(token.getId()).and("revokedAt").is(null)),
                Update.update("revokedAt", Instant.now()), RefreshToken.class).getModifiedCount();
        if (claimed == 0) {
            revokeFamily(token.getFamilyId());
            throw ErrorCode.UNAUTHORIZED.raise();
        }
        return new Rotation(token.getUserId(), token.getFamilyId());
    }

    /** Logout: ends the whole session the token belongs to. Unknown tokens are ignored. */
    public void revokeSessionOf(String raw) {
        RefreshToken token = find(raw);
        if (token != null) {
            revokeFamily(token.getFamilyId());
        }
    }

    public void revokeFamily(String familyId) {
        mongoTemplate.updateMulti(new Query(Criteria.where("familyId").is(familyId).and("revokedAt").is(null)),
                Update.update("revokedAt", Instant.now()), RefreshToken.class);
    }

    private RefreshToken find(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        return mongoTemplate.findOne(new Query(Criteria.where("tokenHash").is(hash(raw))), RefreshToken.class);
    }

    /** SHA-256 is enough here: the token is 256 random bits, so there is nothing to brute-force. */
    static String hash(String raw) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(raw.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException(ex);
        }
    }
}
