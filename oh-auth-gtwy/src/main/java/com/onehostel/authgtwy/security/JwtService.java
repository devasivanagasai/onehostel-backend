package com.onehostel.security;

import java.time.Instant;
import java.util.Date;
import java.util.Optional;
import java.util.UUID;

import javax.crypto.SecretKey;

import org.springframework.stereotype.Service;

import com.onehostel.config.AppProperties;
import com.onehostel.constants.CommonEnumHelper.Role;
import com.onehostel.model.User;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
public class JwtService {

    private static final String CLAIM_EMAIL = "email";
    private static final String CLAIM_ROLE = "role";
    private static final String CLAIM_ORGANIZATION = "org";
    private static final String CLAIM_PHONE = "phone";
    private static final String CLAIM_HOSTEL = "hostel";
    private static final String CLAIM_BED = "bed";

    private final SecretKey key;
    private final AppProperties.Jwt settings;

    public JwtService(AppProperties properties) {
        this.settings = properties.jwt();
        byte[] secret = Decoders.BASE64.decode(settings.secret());
        if (secret.length < 32) {
            throw new IllegalStateException("app.jwt.secret must be Base64 of at least 32 random bytes");
        }
        this.key = Keys.hmacShaKeyFor(secret);
    }

    public record IssuedToken(String token, Instant expiresAt, long expiresInSeconds) {
    }

    public IssuedToken issue(User user) {
        Instant now = Instant.now();
        Instant expiresAt = now.plus(settings.expiration());
        String token = Jwts.builder()
                .id(UUID.randomUUID().toString())
                .issuer(settings.issuer())
                .subject(user.getId())
                .claim(CLAIM_EMAIL, user.getEmail())
                .claim(CLAIM_ROLE, user.getRole().name())
                .claim(CLAIM_PHONE, user.getPhoneNumber())
                .claim(CLAIM_ORGANIZATION, user.getOrganizationId())
                .claim(CLAIM_HOSTEL, user.getHostelId())
                .claim(CLAIM_BED, user.getBedId())
                .issuedAt(Date.from(now))
                .expiration(Date.from(expiresAt))
                .signWith(key, Jwts.SIG.HS256)
                .compact();
        return new IssuedToken(token, expiresAt, settings.expiration().toSeconds());
    }

    public Optional<AuthenticatedUser> verify(String token) {
        try {
            Claims claims = Jwts.parser()
                    .verifyWith(key)
                    .requireIssuer(settings.issuer())
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
            Role role = Role.valueOf(claims.get(CLAIM_ROLE, String.class));
            return Optional.of(new AuthenticatedUser(claims.getSubject(), claims.get(CLAIM_EMAIL, String.class), role,
                    claims.get(CLAIM_ORGANIZATION, String.class), claims.get(CLAIM_HOSTEL, String.class),
                    claims.get(CLAIM_BED, String.class)));
        } catch (JwtException | IllegalArgumentException | NullPointerException ex) {
            log.debug("Rejected token: {}", ex.getMessage());
            return Optional.empty();
        }
    }
}
