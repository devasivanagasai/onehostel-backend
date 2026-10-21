package com.onehostel.core.context;

import com.onehostel.core.security.AuthenticatedUser;

public final class UserContextHolder {

    private static final ThreadLocal<AuthenticatedUser> CURRENT_USER = new ThreadLocal<>();

    private UserContextHolder() {
    }

    public static void set(AuthenticatedUser user) {
        CURRENT_USER.set(user);
    }

    public static AuthenticatedUser get() {
        return CURRENT_USER.get();
    }

    public static String getUserId() {
        AuthenticatedUser user = CURRENT_USER.get();
        return user != null ? user.id() : null;
    }

    public static String getOrganizationId() {
        AuthenticatedUser user = CURRENT_USER.get();
        return user != null ? user.organizationId() : null;
    }

    public static String getHostelId() {
        AuthenticatedUser user = CURRENT_USER.get();
        return user != null ? user.hostelId() : null;
    }

    public static void clear() {
        CURRENT_USER.remove();
    }
}