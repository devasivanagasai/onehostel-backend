package com.onehostel.accesscore.dto.organization;

import com.onehostel.common.constants.CommonEnumHelper.Status;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Create / update payload for an organization. {@code id} and {@code version} only on update. */
public record OrganizationRequest(
        @Size(max = 64) String id,
        Long version,
        @NotBlank @Size(max = 120) String name,
        @Size(max = 64) String ownerUserId,
        Status status) {
}
