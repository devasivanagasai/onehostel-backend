package com.onehostel.common.vo;

import java.util.List;
import java.util.Optional;

import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import com.onehostel.tenancy.constants.IdentityHeaders;

import jakarta.servlet.http.HttpServletRequest;

/**
 * The caller, as the gateway verified it. Resolved once per use from the identity headers; take it as
 * a controller argument ({@code @AuthenticatedUser CurrentUser user}) instead of reading headers.
 *
 * @param userId    OneHostel user id (token claim user_id, else the Keycloak subject)
 * @param subject   Keycloak subject (sub)
 * @param sessionId Keycloak session (sid) of the access token
 * @param clientIp  the client's address as the gateway saw it
 */
public record CurrentUser(
        String userId,
        String username,
        String email,
        List<String> roles,
        String organizationId,
        List<String> parentOrgIds,
        String subject,
        String sessionId,
        String clientIp) {

    public CurrentUser {
        roles = roles == null ? List.of() : List.copyOf(roles);
        parentOrgIds = parentOrgIds == null ? List.of() : List.copyOf(parentOrgIds);
    }

    public static CurrentUser from(HttpServletRequest request) {
        return new CurrentUser(
                blankToNull(request.getHeader(IdentityHeaders.USER_ID)),
                blankToNull(request.getHeader(IdentityHeaders.USERNAME)),
                blankToNull(request.getHeader(IdentityHeaders.USER_EMAIL)),
                IdentityHeaders.splitList(request.getHeader(IdentityHeaders.USER_ROLES)),
                blankToNull(request.getHeader(IdentityHeaders.ORGANIZATION_ID)),
                IdentityHeaders.splitList(request.getHeader(IdentityHeaders.PARENT_ORG_IDS)),
                blankToNull(request.getHeader(IdentityHeaders.AUTH_SUBJECT)),
                blankToNull(request.getHeader(IdentityHeaders.AUTH_SESSION)),
                blankToNull(request.getHeader(IdentityHeaders.CLIENT_IP)));
    }

    /** The caller of the current request; empty outside a request thread. */
    public static Optional<CurrentUser> current() {
        if (RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attributes) {
            return Optional.of(from(attributes.getRequest()));
        }
        return Optional.empty();
    }

    public boolean isAuthenticated() {
        return userId != null;
    }

    public boolean hasRole(String role) {
        return roles.contains(role);
    }

    public boolean isSuperAdmin() {
        return hasRole(IdentityHeaders.SUPER_ADMIN_ROLE);
    }

    /** Who to write into createdBy / updatedBy / audit actor. */
    public String auditName() {
        return userId != null ? userId : "anonymous";
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
