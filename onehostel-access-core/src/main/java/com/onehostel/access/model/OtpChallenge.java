package com.onehostel.access.model;

import java.time.Instant;

import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import com.onehostel.access.constants.AccessEnums.OtpChannel;
import com.onehostel.access.constants.AccessEnums.OtpPurpose;
import com.onehostel.access.constants.CollectionNames;
import com.onehostel.common.model.BaseDocument;
import com.onehostel.tenancy.annotation.GlobalCollection;

import lombok.Getter;
import lombok.Setter;

/** One issued code, stored as a hash of identifier + code. Removed after expiry (TTL). */
@Getter
@Setter
@Document(CollectionNames.OTP_CHALLENGES)
@CompoundIndex(name = "idx_otp_identifier_purpose", def = "{'identifier': 1, 'purpose': 1, 'consumed': 1, 'createdAt': -1}")
@GlobalCollection(reason = "Issued and checked before sign-in, when no tenant is known")
public class OtpChallenge extends BaseDocument {

    private String identifier;

    private String codeHash;

    private OtpPurpose purpose;

    private OtpChannel channel;

    private boolean consumed;

    private int attemptCount;

    @Indexed(expireAfter = "0s")
    private Instant expiresAt;

    private String ipAddress;
}
