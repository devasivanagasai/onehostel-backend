package com.onehostel.model;

import java.time.Instant;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import com.onehostel.constants.CollectionNames;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * A refresh token, stored as the SHA-256 of the random value in the client's cookie. Each
 * refresh revokes the presented token and issues a new one in the same {@code familyId};
 * presenting an already revoked token means it was stolen or replayed, and revokes the family.
 */
@Getter
@Setter
@NoArgsConstructor
@Document(collection = CollectionNames.REFRESH_TOKENS)
public class RefreshToken {

    @Id
    private String id;

    @Indexed(unique = true)
    private String tokenHash;

    @Indexed
    private String userId;

    @Indexed
    private String familyId;

    private Instant createdAt;

    private Instant revokedAt;

    @Indexed(expireAfter = "0s")
    private Instant expiresAt;
}
