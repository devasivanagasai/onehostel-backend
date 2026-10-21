package com.onehostel.core.model;

import java.io.Serializable;
import java.time.Instant;

import com.onehostel.core.model.helper.SearchableDocument;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.CompoundIndexes;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.onehostel.core.constants.CollectionNames;
import com.onehostel.core.constants.EnumHelper.EntityTypeEnum;
import com.onehostel.core.constants.EnumHelper.OtpChannel;
import com.onehostel.core.constants.EnumHelper.OtpPurpose;
import com.onehostel.core.constants.EnumHelper.Role;
import com.onehostel.core.model.helper.OneHostelBaseDoc;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
@CompoundIndexes({
        @CompoundIndex(name = "idx_otp_identifier_purpose", def = "{'OTP0001': 1, 'OTP0010': 1, 'OTP0005': 1}")
})
@Document(collection = CollectionNames.OTP_CHALLENGES)
public class OtpChallengeDoc extends OneHostelBaseDoc implements SearchableDocument, Serializable {

    @JsonIgnore
    private static final long serialVersionUID = 1L;


    @Indexed
    @Field("OTP0001")
    private String identifier;

    @JsonIgnore
    @Field("OTP0002")
    private String otpHash;

    @Field("OTP0003")
    private OtpChannel channel;

    @Field("OTP0004")
    private Role userType;

    @Field("OTP0005")
    private boolean isConsumed;

    @Field("OTP0006")
    private int attemptCount;

    @Indexed(expireAfter = "0s")
    @Field("OTP0007")
    private Instant expiresAt;

    @Field("OTP0008")
    private String ipAddress;

    @Field("OTP0009")
    private String organizationId;

    @Field("OTP0010")
    private OtpPurpose purpose;

    @JsonIgnore
    public CommonSearch getSearchObject() {
        CommonSearch cs = new CommonSearch();
        cs.setEntityEnum(EntityTypeEnum.AUDIT_LOG);
        cs.setEntityModelKey(this.getId());
        cs.setOrganizationId(this.getOrganizationId());

        cs.setCreatedAt(this.getCreatedAt());
        cs.setCreatedBy(this.getCreatedBy());
        cs.setUpdatedAt(this.getUpdatedAt());
        cs.setUpdatedBy(this.getUpdatedBy());

        cs.setText1(this.getId());
        cs.setText2(this.getIdentifier());
        cs.setText3(this.getChannel() != null ? this.getChannel().name() : null);
        cs.setText4(String.valueOf(this.isConsumed()));
        cs.setText5(String.valueOf(this.getAttemptCount()));
        cs.setText6(this.getPurpose() != null ? this.getPurpose().name() : null);

        return cs;
    }
}