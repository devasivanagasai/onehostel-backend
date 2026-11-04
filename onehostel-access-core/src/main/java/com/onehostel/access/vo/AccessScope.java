package com.onehostel.access.vo;

import com.onehostel.access.constants.AccessEnums.Role;

import java.util.List;

/**
 * One place a user may act: an organization (and optionally one hostel in it) with a role there.
 *
 * @param organizationId null only for the platform-wide SUPER_ADMIN
 */
public record AccessScope(String organizationId, String hostelId, Role role, List<String> permissions) {

    public AccessScope {
        permissions = permissions == null ? List.of() : List.copyOf(permissions);
    }
}
