package com.onehostel.access.model;

import java.time.Instant;

import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import com.onehostel.access.constants.CollectionNames;
import com.onehostel.common.model.BaseDocument;
import com.onehostel.tenancy.annotation.GlobalCollection;

import lombok.Getter;
import lombok.Setter;

/** A refresh token we issued, stored by SHA-256 hash only. Rotated on every use; removed after expiry (TTL). */
@Getter
@Setter
@Document(CollectionNames.REFRESH_TOKENS)
@GlobalCollection(reason = "Presented to /refresh-token and /logout without an access token, before a tenant is known")
public class RefreshToken extends BaseDocument {

    @Indexed(unique = true)
    private String tokenHash;

    @Indexed
    private String userId;

    /** Keycloak session (sid), so one session can be kept while the others are revoked. */
    private String keycloakSessionId;

    private boolean revoked;

    /** Set when this token was exchanged for a new one; a second use of it means it leaked. */
    private String replacedByTokenHash;

    @Indexed(expireAfter = "0s")
    private Instant expiresAt;

    private String deviceId;

    private String ipAddress;

    private String userAgent;
}
