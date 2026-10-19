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
 * One OTP sent (or pretended to be sent, for unknown identifiers) to a login identifier.
 * Only the code's BCrypt hash is stored. Kept until {@code purgeAt} so sends can be counted
 * over the whole send window, long after the code itself expired.
 */
@Getter
@Setter
@NoArgsConstructor
@Document(collection = CollectionNames.OTP_CHALLENGES)
public class OtpChallenge {

    @Id
    private String id;

    /** {@code <USER_TYPE>:<normalized identifier>}. */
    @Indexed
    private String challengeKey;

    private String codeHash;

    private int attempts;

    private boolean consumed;

    private Instant createdAt;

    private Instant expiresAt;

    @Indexed(expireAfter = "0s")
    private Instant purgeAt;
}
