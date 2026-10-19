package com.onehostel.security;

import com.onehostel.constants.CommonEnumHelper.Role;

public record AuthenticatedUser(String id, String email, Role role, String organizationId, String hostelId,
        String bedId) {

    public boolean isSuperAdmin() {
        return role == Role.SUPER_ADMIN;
    }
}
