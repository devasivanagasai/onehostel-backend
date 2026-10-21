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
        @CompoundIndex(name = "idx_token_user_revoked", def = "{'RT0002': 1, 'RT0004': 1}"),
        @CompoundIndex(name = "idx_token_hash", def = "{'RT0001': 1}", unique = true)
})
@Document(collection = CollectionNames.REFRESH_TOKENS)
public class RefreshTokenDoc extends OneHostelBaseDoc implements SearchableDocument, Serializable {

    @JsonIgnore
    private static final long serialVersionUID = 1L;


    @Indexed
    @Field("RT0001")
    private String tokenHash;

    @Indexed
    @Field("RT0002")
    private String userId;

    @Field("RT0003")
    private String organizationId;

    @Field("RT0004")
    private boolean isRevoked;

    @Indexed(expireAfter = "0s")
    @Field("RT0005")
    private Instant expiresAt;

    @Field("RT0006")
    private String ipAddress;

    @Field("RT0007")
    private String userAgent;

    @Field("RT0008")
    private String deviceId;

    @Field("RT0009")
    private String keycloakSessionId;

    @Field("RT0010")
    private String replacedByTokenHash;

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
        cs.setText2(this.getUserId());
        cs.setText3(String.valueOf(this.isRevoked()));
        cs.setText4(this.getDeviceId());
        cs.setText5(this.getIpAddress());
        cs.setText6(this.getKeycloakSessionId());

        return cs;
    }
}