package com.onehostel.model.helper;

import java.time.Instant;
import java.util.Map;

import org.springframework.data.annotation.CreatedBy;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.LastModifiedBy;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.annotation.Version;
import org.springframework.data.mongodb.core.index.Indexed;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public abstract class HostelAbstractDocument {

    @Id
    private String id;

    @Indexed
    private String organizationId;

    @Indexed(sparse = true)
    private String hostelBranchId;

    private String unitId;

    @CreatedBy
    private String createdUser;

    @LastModifiedBy
    private String updatedUser;

    @CreatedDate
    private Instant createdDateTime;

    @LastModifiedDate
    private Instant updatedDateTime;

    @Version
    private Long version;

    private Map<String, Object> unmappedData;
}
