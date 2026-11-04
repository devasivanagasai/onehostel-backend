package com.onehostel.access.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Keycloak's token reply, returned to the client as-is (the Angular app reads these snake_case names).
 *
 * @param sessionState the Keycloak session id (sid)
 */
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
public record KeycloakTokenResponse(
        @JsonProperty("access_token") String accessToken,
        @JsonProperty("expires_in") Long expiresIn,
        @JsonProperty("refresh_token") String refreshToken,
        @JsonProperty("refresh_expires_in") Long refreshExpiresIn,
        @JsonProperty("token_type") String tokenType,
        @JsonProperty("session_state") String sessionState,
        @JsonProperty("scope") String scope) {
}
