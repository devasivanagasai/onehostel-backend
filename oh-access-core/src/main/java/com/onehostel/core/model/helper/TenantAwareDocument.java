package com.onehostel.core.model.helper;

import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Field;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public abstract class TenantAwareDocument extends OneHostelBaseDoc {

    @Indexed
    @Field("organization_id")
    private String organizationId;

    @Indexed
    @Field("hostel_id")
    private String hostelId;
}