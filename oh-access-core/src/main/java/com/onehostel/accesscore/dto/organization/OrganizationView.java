package com.onehostel.accesscore.dto.organization;

import java.time.Instant;

import com.onehostel.accesscore.model.Organization;
import com.onehostel.common.constants.CommonEnumHelper.Status;

/** An organization as the API returns it. Send {@code version} back on update. */
public record OrganizationView(
        String id,
        String name,
        String ownerUserId,
        Status status,
        Long version,
        Instant createdDateTime,
        Instant updatedDateTime) {

    public static OrganizationView from(Organization organization) {
        return new OrganizationView(organization.getId(), organization.getName(), organization.getOwnerUserId(),
                organization.getStatus(), organization.getVersion(), organization.getCreatedDateTime(),
                organization.getUpdatedDateTime());
    }
}
