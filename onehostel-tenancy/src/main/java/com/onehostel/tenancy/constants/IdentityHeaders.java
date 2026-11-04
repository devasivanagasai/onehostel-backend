package com.onehostel.tenancy.constants;

import java.util.Arrays;
import java.util.List;

/**
 * Headers the gateway sets from the verified access token. It strips any client-supplied copy first,
 * so downstream services can trust them (InternalSecretFilter proves the request came through it).
 * Services read them only through TenantResolutionFilter and the CurrentUser resolver.
 */
public final class IdentityHeaders {

    public static final String ORGANIZATION_ID = "X-Organization-Id";
    /** Comma-separated ancestor organization ids, nearest first. */
    public static final String PARENT_ORG_IDS = "X-Parent-Org-Ids";
    /** OneHostel user id (token claim user_id, else the Keycloak subject). */
    public static final String USER_ID = "X-User-Id";
    public static final String USERNAME = "X-Username";
    public static final String USER_EMAIL = "X-User-Email";
    /** Comma-separated roles, e.g. HOSTEL_OWNER. */
    public static final String USER_ROLES = "X-User-Roles";
    /** Keycloak subject (sub) of the access token. */
    public static final String AUTH_SUBJECT = "X-Auth-Subject";
    /** Keycloak session id (sid) of the access token. */
    public static final String AUTH_SESSION = "X-Auth-Session";
    /** The client's address as the gateway saw it on the socket. */
    public static final String CLIENT_IP = "X-Client-Ip";
    /** Shared secret proving a request came through the gateway. */
    public static final String INTERNAL_SECRET = "X-Internal-Secret";

    /** Every header the gateway owns; removed from client requests before it sets its own. */
    public static final List<String> ALL = List.of(ORGANIZATION_ID, PARENT_ORG_IDS, USER_ID, USERNAME, USER_EMAIL,
            USER_ROLES, AUTH_SUBJECT, AUTH_SESSION, CLIENT_IP, INTERNAL_SECRET);

    /** Role that sees every organization. */
    public static final String SUPER_ADMIN_ROLE = "SUPER_ADMIN";

    private IdentityHeaders() {
    }

    public static List<String> splitList(String headerValue) {
        if (headerValue == null || headerValue.isBlank()) {
            return List.of();
        }
        return Arrays.stream(headerValue.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .toList();
    }
}
