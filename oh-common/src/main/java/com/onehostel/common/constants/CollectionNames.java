package com.onehostel.common.constants;

public final class CollectionNames {

    // One registry of codes for every service, so a code is never reused. Each service only
    // creates the collections it owns, in its own database.

    // --- oh-auth-gtwy (database onehostel_auth) ---
    public static final String USERS = "C0001";
    public static final String OTP_CHALLENGES = "C0007";
    public static final String REFRESH_TOKENS = "C0008";
    public static final String PUBLIC_ENDPOINTS = "C0011";

    // --- oh-access-core (database onehostel_core) ---
    public static final String HOSTELS = "C0002";
    public static final String ROOMS = "C0003";
    public static final String BOOKINGS = "C0004";
    public static final String AUDIT_LOGS = "C0005";
    public static final String SYSTEM_SETTINGS = "C0006";
    public static final String ORGANIZATIONS = "C0010";

    // --- both: each database has its own search mirror of the records it owns ---
    public static final String COMMON_SEARCH = "C0009";

    private CollectionNames() {
    }
}
