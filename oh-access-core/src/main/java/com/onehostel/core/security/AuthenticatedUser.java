package com.onehostel.core.security;

import com.onehostel.core.constants.EnumHelper.Role;

import java.io.Serializable;

public record AuthenticatedUser(
        String id,
        String email,
        Role role,
        String organizationId,
        String hostelId,
        String bedId
) implements Serializable {

    public boolean isSuperAdmin() {
        return role == Role.SUPER_ADMIN;
    }

    public boolean isOwner() {
        return role == Role.HOSTEL_OWNER;
    }

    public boolean isTenant() {
        return role == Role.HOSTEL_TENANT;
    }
}