package com.onehostel.access.client;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import com.onehostel.access.config.KeycloakProperties;
import com.onehostel.access.exception.ErrorCode;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/** Keycloak admin REST calls, made with the service client's own token. */
@Slf4j
@Component
@RequiredArgsConstructor
public class KeycloakAdminClient {

    private static final ParameterizedTypeReference<List<Map<String, Object>>> LIST_OF_MAPS = new ParameterizedTypeReference<>() { };
    private static final ParameterizedTypeReference<Map<String, Object>> MAP = new ParameterizedTypeReference<>() { };

    private final RestClient keycloakRestClient;
    private final KeycloakProperties properties;
    private final KeycloakAuthClient authClient;

    /** Keycloak id for an exact username; empty when there is no such user. */
    public Optional<String> findUserId(String username) {
        String token = authClient.serviceAccessToken();
        List<Map<String, Object>> users = KeycloakAuthClient.call(() -> keycloakRestClient.get()
                        .uri(properties.adminUrl() + "/users?exact=true&username={username}", username)
                        .headers(h -> h.setBearerAuth(token))
                        .accept(MediaType.APPLICATION_JSON)
                        .retrieve()
                        .body(LIST_OF_MAPS),
                e -> ErrorCode.IDENTITY_PROVIDER_UNAVAILABLE.raiseCausedBy(e));
        return users == null || users.isEmpty() ? Optional.empty() : Optional.of(String.valueOf(users.get(0).get("id")));
    }

    /** The stored Keycloak id, else looked up by username; ACCOUNT_NOT_LINKED when Keycloak has no such user. */
    public String requireUserId(String storedKeycloakUserId, String username) {
        if (storedKeycloakUserId != null) {
            return storedKeycloakUserId;
        }
        return findUserId(username).orElseThrow(() -> {
            log.error("No Keycloak account for username {}", username);
            return ErrorCode.ACCOUNT_NOT_LINKED.raise();
        });
    }

    /** Sets a new permanent password. Keycloak's password policy rejects a weak one with 400. */
    public void resetPassword(String keycloakUserId, String newPassword) {
        String token = authClient.serviceAccessToken();
        Map<String, Object> credential = Map.of("type", "password", "value", newPassword, "temporary", false);
        KeycloakAuthClient.call(() -> keycloakRestClient.put()
                        .uri(properties.adminUrl() + "/users/{id}/reset-password", keycloakUserId)
                        .headers(h -> h.setBearerAuth(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .body(credential)
                        .retrieve()
                        .toBodilessEntity(),
                e -> {
                    if (e.getStatusCode() == HttpStatus.BAD_REQUEST) {
                        log.warn("Keycloak rejected the new password: {}", e.getResponseBodyAsString());
                        return ErrorCode.PASSWORD_POLICY_VIOLATED.raise();
                    }
                    return ErrorCode.IDENTITY_PROVIDER_UNAVAILABLE.raiseCausedBy(e);
                });
    }

    /**
     * Ends every Keycloak session of the user except keepSessionId. Best-effort: the password already
     * changed and our stored refresh tokens are revoked; a missed session only lives until it expires.
     */
    public int logoutOtherSessions(String keycloakUserId, String keepSessionId) {
        try {
            String token = authClient.serviceAccessToken();
            List<Map<String, Object>> sessions = keycloakRestClient.get()
                    .uri(properties.adminUrl() + "/users/{id}/sessions", keycloakUserId)
                    .headers(h -> h.setBearerAuth(token))
                    .accept(MediaType.APPLICATION_JSON)
                    .retrieve()
                    .body(LIST_OF_MAPS);
            int ended = 0;
            for (Map<String, Object> session : sessions == null ? List.<Map<String, Object>>of() : sessions) {
                String sessionId = String.valueOf(session.get("id"));
                if (!sessionId.equals(keepSessionId)) {
                    keycloakRestClient.delete().uri(properties.adminUrl() + "/sessions/{sid}", sessionId)
                            .headers(h -> h.setBearerAuth(token)).retrieve().toBodilessEntity();
                    ended++;
                }
            }
            return ended;
        } catch (RuntimeException e) {
            log.warn("Could not end other Keycloak sessions of {} (best-effort)", keycloakUserId, e);
            return 0;
        }
    }

    /** Ends every Keycloak session of the user. Best-effort, as logoutOtherSessions. */
    public void logoutAllSessions(String keycloakUserId) {
        try {
            String token = authClient.serviceAccessToken();
            keycloakRestClient.post().uri(properties.adminUrl() + "/users/{id}/logout", keycloakUserId)
                    .headers(h -> h.setBearerAuth(token)).retrieve().toBodilessEntity();
        } catch (RuntimeException e) {
            log.warn("Could not end Keycloak sessions of {} (best-effort)", keycloakUserId, e);
        }
    }

    /**
     * Sets the name and email Keycloak puts in the user's tokens. Read-modify-write of the whole user, so
     * attributes such as user_id (the token's user_id claim) are sent back unchanged rather than dropped.
     */
    public void updateProfile(String keycloakUserId, String firstName, String lastName, String email) {
        String token = authClient.serviceAccessToken();
        Map<String, Object> current = KeycloakAuthClient.call(() -> keycloakRestClient.get()
                        .uri(properties.adminUrl() + "/users/{id}", keycloakUserId)
                        .headers(h -> h.setBearerAuth(token))
                        .accept(MediaType.APPLICATION_JSON)
                        .retrieve()
                        .body(MAP),
                e -> ErrorCode.IDENTITY_PROVIDER_UNAVAILABLE.raiseCausedBy(e));
        Map<String, Object> updated = new HashMap<>(current == null ? Map.of() : current);
        updated.put("firstName", firstName);
        updated.put("lastName", lastName);
        updated.put("email", email);
        KeycloakAuthClient.call(() -> keycloakRestClient.put()
                        .uri(properties.adminUrl() + "/users/{id}", keycloakUserId)
                        .headers(h -> h.setBearerAuth(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .body(updated)
                        .retrieve()
                        .toBodilessEntity(),
                e -> {
                    if (e.getStatusCode() == HttpStatus.CONFLICT) {
                        return ErrorCode.EMAIL_IN_USE.raise();
                    }
                    if (e.getStatusCode() == HttpStatus.BAD_REQUEST) {
                        // Keycloak's user profile rules, e.g. characters not allowed in a name
                        log.warn("Keycloak rejected a profile update: {}", e.getResponseBodyAsString());
                        return ErrorCode.PROFILE_REJECTED.raise("Name or email contains characters that are not allowed.");
                    }
                    return ErrorCode.IDENTITY_PROVIDER_UNAVAILABLE.raiseCausedBy(e);
                });
    }
}
