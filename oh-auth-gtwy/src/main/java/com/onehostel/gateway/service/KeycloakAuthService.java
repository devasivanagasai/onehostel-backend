package com.onehostel.gateway.service;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.onehostel.core.exception.ErrorCode;
import com.onehostel.core.security.KeycloakConstants;
import com.onehostel.core.security.KeycloakTokenResponse;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Mono;

@Slf4j
@Service
@RequiredArgsConstructor
public class KeycloakAuthService {

    private static final ObjectMapper JSON = new ObjectMapper();

    private final WebClient keycloakWebClient;

    @Value("${keycloak.token-endpoint}")
    private String tokenEndpoint;

    @Value("${keycloak.revoke-endpoint}")
    private String revokeEndpoint;

    @Value("${keycloak.logout-endpoint}")
    private String logoutEndpoint;

    @Value("${keycloak.client-id}")
    private String clientId;

    @Value("${keycloak.client-secret:}")
    private String clientSecret;

    @Value("${keycloak.service-client-id}")
    private String serviceClientId;

    @Value("${keycloak.service-client-secret}")
    private String serviceClientSecret;

    public Mono<KeycloakTokenResponse> loginWithPassword(String username, String password) {
        MultiValueMap<String, String> formData = clientForm(clientId, clientSecret);
        formData.add("grant_type", KeycloakConstants.GRANT_TYPE_PASSWORD);
        formData.add("username", username);
        formData.add("password", password);

        return executeTokenRequest(formData)
                .onErrorMap(e -> ErrorCode.INVALID_CREDENTIALS.raise());
    }

    /**
     * Checks a password by attempting a login. The probe session Keycloak opens on success is
     * closed straight away, so verifying doesn't leave an extra session behind.
     */
    public Mono<Boolean> isPasswordCorrect(String username, String password) {
        MultiValueMap<String, String> formData = clientForm(clientId, clientSecret);
        formData.add("grant_type", KeycloakConstants.GRANT_TYPE_PASSWORD);
        formData.add("username", username);
        formData.add("password", password);

        return keycloakWebClient.post()
                .uri(tokenEndpoint)
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .body(BodyInserters.fromFormData(formData))
                .retrieve()
                .bodyToMono(KeycloakTokenResponse.class)
                .flatMap(probe -> endSession(probe.getRefreshToken()).thenReturn(true))
                .onErrorResume(WebClientResponseException.class, e -> {
                    // 401 invalid_grant = wrong password; 400 = account disabled or similar
                    if (e.getStatusCode().value() == 401 || e.getStatusCode().value() == 400) {
                        return Mono.just(false);
                    }
                    log.error("Keycloak password check failed: {} {}", e.getStatusCode(), e.getResponseBodyAsString());
                    return Mono.error(ErrorCode.INTERNAL_ERROR.raiseCausedBy(e));
                });
    }

    /** Ends the Keycloak session a refresh token belongs to (OIDC logout). */
    public Mono<Void> endSession(String refreshToken) {
        if (refreshToken == null) {
            return Mono.empty();
        }
        MultiValueMap<String, String> formData = clientFormFor(refreshToken);
        formData.add("refresh_token", refreshToken);

        return keycloakWebClient.post()
                .uri(logoutEndpoint)
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .body(BodyInserters.fromFormData(formData))
                .retrieve()
                .bodyToMono(Void.class)
                .onErrorResume(e -> {
                    log.warn("Keycloak logout failed: {}", e.getMessage());
                    return Mono.empty();
                });
    }

    /**
     * Refreshes with the client the token was issued to: password logins belong to the
     * frontend client, OTP logins (token exchange) to the service client.
     */
    public Mono<KeycloakTokenResponse> refreshAccessToken(String refreshToken) {
        MultiValueMap<String, String> formData = clientFormFor(refreshToken);
        formData.add("grant_type", KeycloakConstants.GRANT_TYPE_REFRESH_TOKEN);
        formData.add("refresh_token", refreshToken);

        return executeTokenRequest(formData);
    }

    /**
     * Issues tokens for a user whose identity the gateway has already verified (OTP login).
     * They belong to the service client (no "audience"): Keycloak 26 can't refresh exchanged
     * tokens issued for another client, so refresh/revoke pick the client from the token's azp.
     */
    public Mono<KeycloakTokenResponse> exchangeForUser(String keycloakUserId) {
        MultiValueMap<String, String> formData = clientForm(serviceClientId, serviceClientSecret);
        formData.add("grant_type", KeycloakConstants.GRANT_TYPE_TOKEN_EXCHANGE);
        formData.add("requested_subject", keycloakUserId);
        formData.add("requested_token_type", KeycloakConstants.TOKEN_TYPE_REFRESH_TOKEN);

        return executeTokenRequest(formData);
    }

    /** Access token of the service client itself, for the Keycloak admin API. */
    public Mono<String> serviceAccessToken() {
        MultiValueMap<String, String> formData = clientForm(serviceClientId, serviceClientSecret);
        formData.add("grant_type", KeycloakConstants.GRANT_TYPE_CLIENT_CREDENTIALS);

        return keycloakWebClient.post()
                .uri(tokenEndpoint)
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .body(BodyInserters.fromFormData(formData))
                .retrieve()
                .bodyToMono(KeycloakTokenResponse.class)
                .map(KeycloakTokenResponse::getAccessToken)
                .onErrorMap(e -> {
                    log.error("Keycloak service-account login failed: {}", e.getMessage());
                    return ErrorCode.INTERNAL_ERROR.raiseCausedBy(e);
                });
    }

    public Mono<Void> revokeToken(String refreshToken) {
        MultiValueMap<String, String> formData = clientFormFor(refreshToken);
        formData.add("token", refreshToken);
        formData.add("token_type_hint", "refresh_token");

        return keycloakWebClient.post()
                .uri(revokeEndpoint)
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .body(BodyInserters.fromFormData(formData))
                .retrieve()
                .bodyToMono(Void.class)
                .onErrorResume(e -> {
                    log.warn("Keycloak revocation failed: {}", e.getMessage());
                    return Mono.empty();
                });
    }

    private Mono<KeycloakTokenResponse> executeTokenRequest(MultiValueMap<String, String> formData) {
        return keycloakWebClient.post()
                .uri(tokenEndpoint)
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .body(BodyInserters.fromFormData(formData))
                .retrieve()
                .bodyToMono(KeycloakTokenResponse.class)
                .onErrorMap(e -> {
                    log.error("Keycloak token exchange error: {}", e.getMessage());
                    return ErrorCode.UNAUTHORIZED.raiseWithMessage("Authentication failed: invalid credentials or expired session");
                });
    }

    private MultiValueMap<String, String> clientFormFor(String refreshToken) {
        return serviceClientId.equals(authorizedParty(refreshToken))
                ? clientForm(serviceClientId, serviceClientSecret)
                : clientForm(clientId, clientSecret);
    }

    private static MultiValueMap<String, String> clientForm(String id, String secret) {
        MultiValueMap<String, String> formData = new LinkedMultiValueMap<>();
        formData.add("client_id", id);
        if (secret != null && !secret.isBlank()) {
            formData.add("client_secret", secret);
        }
        return formData;
    }

    /** Reads the "azp" claim; Keycloak validates the token itself, so no signature check here. */
    private static String authorizedParty(String token) {
        try {
            String[] parts = token.split("\\.");
            if (parts.length < 2) {
                return null;
            }
            JsonNode claims = JSON.readTree(new String(Base64.getUrlDecoder().decode(parts[1]), StandardCharsets.UTF_8));
            return claims.path("azp").asText(null);
        } catch (Exception e) {
            return null;
        }
    }
}
