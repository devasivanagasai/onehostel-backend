package com.onehostel.gateway.service;

import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import com.onehostel.core.exception.ErrorCode;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/** Keycloak admin REST calls made with the oh-auth-gtwy-service account. */
@Slf4j
@Service
@RequiredArgsConstructor
public class KeycloakAdminService {

    private static final ParameterizedTypeReference<List<Map<String, Object>>> USER_LIST =
            new ParameterizedTypeReference<>() { };

    private final WebClient keycloakWebClient;
    private final KeycloakAuthService keycloakAuthService;

    @Value("${keycloak.admin-url}")
    private String adminUrl;

    /** Keycloak id for an exact username; empty when there is no such user. */
    public Mono<String> findUserId(String username) {
        return keycloakAuthService.serviceAccessToken()
                .flatMap(token -> keycloakWebClient.get()
                        .uri(adminUrl + "/users?exact=true&username={username}", username)
                        .headers(h -> h.setBearerAuth(token))
                        .accept(MediaType.APPLICATION_JSON)
                        .retrieve()
                        .bodyToMono(USER_LIST))
                .flatMap(users -> users.isEmpty()
                        ? Mono.<String>empty()
                        : Mono.just(String.valueOf(users.get(0).get("id"))))
                .onErrorMap(WebClientResponseException.class, e -> {
                    log.error("Keycloak user lookup failed: {} {}", e.getStatusCode(), e.getResponseBodyAsString());
                    return ErrorCode.INTERNAL_ERROR.raiseCausedBy(e);
                });
    }

    /** Sets a new permanent password (PUT /users/{id}/reset-password). */
    public Mono<Void> resetPassword(String keycloakUserId, String newPassword) {
        Map<String, Object> credential = Map.of("type", "password", "value", newPassword, "temporary", false);

        return keycloakAuthService.serviceAccessToken()
                .flatMap(token -> keycloakWebClient.put()
                        .uri(adminUrl + "/users/{id}/reset-password", keycloakUserId)
                        .headers(h -> h.setBearerAuth(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .bodyValue(credential)
                        .retrieve()
                        .toBodilessEntity())
                .then()
                .onErrorMap(WebClientResponseException.BadRequest.class, e -> {
                    // Keycloak password policy violations come back as 400 with a readable message
                    log.warn("Keycloak rejected the new password: {}", e.getResponseBodyAsString());
                    return ErrorCode.VALIDATION_FAILED.raiseWithMessage("The new password does not meet the password policy");
                })
                .onErrorMap(WebClientResponseException.class, e -> {
                    log.error("Keycloak password reset failed: {} {}", e.getStatusCode(), e.getResponseBodyAsString());
                    return ErrorCode.INTERNAL_ERROR.raiseCausedBy(e);
                });
    }

    /** Ends every Keycloak session of the user except keepSessionId (the caller's current session). */
    public Mono<Long> logoutOtherSessions(String keycloakUserId, String keepSessionId) {
        return keycloakAuthService.serviceAccessToken()
                .flatMap(token -> keycloakWebClient.get()
                        .uri(adminUrl + "/users/{id}/sessions", keycloakUserId)
                        .headers(h -> h.setBearerAuth(token))
                        .accept(MediaType.APPLICATION_JSON)
                        .retrieve()
                        .bodyToMono(USER_LIST)
                        .flatMapMany(Flux::fromIterable)
                        .map(session -> String.valueOf(session.get("id")))
                        .filter(sessionId -> !sessionId.equals(keepSessionId))
                        .flatMap(sessionId -> keycloakWebClient.delete()
                                .uri(adminUrl + "/sessions/{sid}", sessionId)
                                .headers(h -> h.setBearerAuth(token))
                                .retrieve()
                                .toBodilessEntity())
                        .count())
                .onErrorResume(e -> {
                    log.warn("Could not end other Keycloak sessions for {}: {}", keycloakUserId, e.getMessage());
                    return Mono.just(0L);
                });
    }

    /** Ends every Keycloak session of the user, so existing refresh tokens stop working there too. */
    public Mono<Void> logoutAllSessions(String keycloakUserId) {
        return keycloakAuthService.serviceAccessToken()
                .flatMap(token -> keycloakWebClient.post()
                        .uri(adminUrl + "/users/{id}/logout", keycloakUserId)
                        .headers(h -> h.setBearerAuth(token))
                        .retrieve()
                        .toBodilessEntity())
                .then()
                .onErrorResume(e -> {
                    log.warn("Could not end Keycloak sessions for {}: {}", keycloakUserId, e.getMessage());
                    return Mono.empty();
                });
    }
}
