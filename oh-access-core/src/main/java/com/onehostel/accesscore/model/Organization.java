package com.onehostel.accesscore.model;

import java.time.Instant;

import org.springframework.data.annotation.CreatedBy;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.LastModifiedBy;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.annotation.Version;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import com.onehostel.common.constants.CollectionNames;
import com.onehostel.common.constants.CommonEnumHelper.Status;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * A hostel owner's property group: the tenant root. Its id is the {@code organizationId} carried
 * by users (in oh-auth-gtwy) and by every tenant-owned record here.
 */
@Getter
@Setter
@NoArgsConstructor
@Document(collection = CollectionNames.ORGANIZATIONS)
public class Organization {

    @Id
    private String id;

    @Indexed
    private String name;

    /** The owning user's id in oh-auth-gtwy; null until an owner is linked. */
    @Indexed(sparse = true)
    private String ownerUserId;

    private Status status = Status.ACTIVE;

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

    public Organization(String name) {
        this.name = name;
    }
}
