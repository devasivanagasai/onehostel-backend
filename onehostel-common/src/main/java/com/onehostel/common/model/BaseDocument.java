package com.onehostel.common.model;

import java.time.Instant;
import java.util.List;

import org.springframework.data.annotation.CreatedBy;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.LastModifiedBy;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.annotation.Version;

import com.onehostel.tenancy.model.TenantOwned;

import lombok.Getter;
import lombok.Setter;

/**
 * Base of every OneHostel document. Tenant-owned by default (organizationId, ENFORCE-scoped by
 * onehostel-tenancy); mark platform-wide classes with @GlobalCollection. Fields keep their camelCase
 * names in Mongo. Give each feature a compound index such as {organizationId:1, deleted:1, createdAt:-1}.
 */
@Getter
@Setter
public abstract class BaseDocument implements TenantOwned {

    /** ULID, assigned on first save by UlidIdCallback. */
    @Id
    private String id;

    /** Tenant (hostel / branch). Set from TenantContext on create; never taken from a request body. */
    private String organizationId;

    /** Ancestor chain (group -> hostel -> branch) for ancestor-scoped reads. */
    private List<String> parentOrgIds;

    @CreatedBy
    private String createdBy;

    @CreatedDate
    private Instant createdAt;

    @LastModifiedBy
    private String updatedBy;

    @LastModifiedDate
    private Instant updatedAt;

    /** Optimistic locking: an update with a stale version fails with OPTIMISTIC_LOCK_FAILED. */
    @Version
    private Long version;

    /** Soft delete: every base query adds deleted != true. */
    private boolean deleted;

    private Instant deletedAt;

    private String deletedBy;
}
