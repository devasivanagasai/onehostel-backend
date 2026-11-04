package com.onehostel.access.client;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.function.Function;
import java.util.function.Supplier;

import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.onehostel.access.config.KeycloakProperties;
import com.onehostel.access.dto.KeycloakTokenResponse;
import com.onehostel.access.exception.ErrorCode;
import com.onehostel.common.exception.HostelException;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Keycloak's OpenID Connect endpoints. The integration edge: every Keycloak failure leaves this class as a
 * catalogue error (wrong credentials, expired session, or IDENTITY_PROVIDER_UNAVAILABLE), never as a raw
 * HTTP client exception.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class KeycloakAuthClient {

    static final String GRANT_PASSWORD = "password";
    static final String GRANT_REFRESH_TOKEN = "refresh_token";
    static final String GRANT_CLIENT_CREDENTIALS = "client_credentials";
    static final String GRANT_TOKEN_EXCHANGE = "urn:ietf:params:oauth:grant-type:token-exchange";
    static final String TOKEN_TYPE_REFRESH_TOKEN = "urn:ietf:params:oauth:token-type:refresh_token";

    private final RestClient keycloakRestClient;
    private final KeycloakProperties properties;
    private final ObjectMapper objectMapper;

    public KeycloakTokenResponse loginWithPassword(String username, String password) {
        MultiValueMap<String, String> form = clientForm(properties.clientId(), properties.clientSecret());
        form.add("grant_type", GRANT_PASSWORD);
        form.add("username", username);
        form.add("password", password);
        return call(() -> tokenRequest(form), e -> ErrorCode.INVALID_CREDENTIALS.raise());
    }

    /**
     * Checks a password by attempting a login. The probe session Keycloak opens on success is closed
     * straight away, so checking doesn't leave an extra session behind.
     */
    public boolean isPasswordCorrect(String username, String password) {
        try {
            KeycloakTokenResponse probe = loginWithPassword(username, password);
            endSession(probe.refreshToken());
            return true;
        } catch (HostelException e) {
            if (e.getErrorCode() == ErrorCode.INVALID_CREDENTIALS) {
                return false;
            }
            throw e;
        }
    }

    /**
     * Refreshes with the client the token was issued to: password logins belong to the
     * frontend client, OTP logins (token exchange) to the service client.
     */
    public KeycloakTokenResponse refresh(String refreshToken) {
        MultiValueMap<String, String> form = clientFormFor(refreshToken);
        form.add("grant_type", GRANT_REFRESH_TOKEN);
        form.add("refresh_token", refreshToken);
        return call(() -> tokenRequest(form), e -> ErrorCode.INVALID_REFRESH_TOKEN.raise());
    }

    /**
     * Issues tokens for a user whose identity access-core has already verified (OTP login). They belong to
     * the service client: Keycloak 26 can't refresh exchanged tokens issued for another client.
     */
    public KeycloakTokenResponse exchangeForUser(String keycloakUserId) {
        MultiValueMap<String, String> form = clientForm(properties.serviceClientId(), properties.serviceClientSecret());
        form.add("grant_type", GRANT_TOKEN_EXCHANGE);
        form.add("requested_subject", keycloakUserId);
        form.add("requested_token_type", TOKEN_TYPE_REFRESH_TOKEN);
        return call(() -> tokenRequest(form), e -> {
            log.error("Keycloak token exchange refused ({}): {} - is configure-token-exchange.ps1 applied?",
                    e.getStatusCode(), e.getResponseBodyAsString());
            return ErrorCode.IDENTITY_PROVIDER_UNAVAILABLE.raiseCausedBy(e);
        });
    }

    /** Access token of the service client itself, for the admin API. */
    public String serviceAccessToken() {
        MultiValueMap<String, String> form = clientForm(properties.serviceClientId(), properties.serviceClientSecret());
        form.add("grant_type", GRANT_CLIENT_CREDENTIALS);
        return call(() -> tokenRequest(form), e -> {
            log.error("Keycloak service-account login refused ({}): check KEYCLOAK_SERVICE_CLIENT_SECRET", e.getStatusCode());
            return ErrorCode.IDENTITY_PROVIDER_UNAVAILABLE.raiseCausedBy(e);
        }).accessToken();
    }

    /** Ends the Keycloak session a refresh token belongs to. Best-effort: the session expires anyway. */
    public void endSession(String refreshToken) {
        if (refreshToken == null) {
            return;
        }
        MultiValueMap<String, String> form = clientFormFor(refreshToken);
        form.add("refresh_token", refreshToken);
        bestEffortPost(properties.logoutEndpoint(), form, "logout");
    }

    /** Revokes a refresh token. Best-effort: our stored copy is already revoked, which is what refresh checks. */
    public void revoke(String refreshToken) {
        MultiValueMap<String, String> form = clientFormFor(refreshToken);
        form.add("token", refreshToken);
        form.add("token_type_hint", "refresh_token");
        bestEffortPost(properties.revokeEndpoint(), form, "revocation");
    }

    private KeycloakTokenResponse tokenRequest(MultiValueMap<String, String> form) {
        return keycloakRestClient.post()
                .uri(properties.tokenEndpoint())
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .body(form)
                .retrieve()
                .body(KeycloakTokenResponse.class);
    }

    private void bestEffortPost(String uri, MultiValueMap<String, String> form, String what) {
        try {
            keycloakRestClient.post().uri(uri).contentType(MediaType.APPLICATION_FORM_URLENCODED).body(form)
                    .retrieve().toBodilessEntity();
        } catch (RestClientException e) {
            log.warn("Keycloak {} failed (best-effort, continuing)", what, e);
        }
    }

    /** 4xx -> the caller's mapping; anything else (5xx, timeout, connection refused) -> unavailable. */
    static <T> T call(Supplier<T> request, Function<HttpClientErrorException, HostelException> onClientError) {
        try {
            return request.get();
        } catch (HttpClientErrorException e) {
            throw onClientError.apply(e);
        } catch (RestClientException e) {
            throw ErrorCode.IDENTITY_PROVIDER_UNAVAILABLE.raiseCausedBy(e);
        }
    }

    private MultiValueMap<String, String> clientFormFor(String refreshToken) {
        return properties.serviceClientId().equals(authorizedParty(refreshToken))
                ? clientForm(properties.serviceClientId(), properties.serviceClientSecret())
                : clientForm(properties.clientId(), properties.clientSecret());
    }

    private static MultiValueMap<String, String> clientForm(String id, String secret) {
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("client_id", id);
        if (secret != null && !secret.isBlank()) {
            form.add("client_secret", secret);
        }
        return form;
    }

    /** Reads the "azp" claim to pick the client. Keycloak validates the token itself, so no signature check here. */
    private String authorizedParty(String token) {
        try {
            String[] parts = token.split("\\.");
            if (parts.length < 2) {
                return null;
            }
            JsonNode claims = objectMapper.readTree(new String(Base64.getUrlDecoder().decode(parts[1]), StandardCharsets.UTF_8));
            return claims.path("azp").asText(null);
        } catch (Exception e) {
            return null;
        }
    }
}
