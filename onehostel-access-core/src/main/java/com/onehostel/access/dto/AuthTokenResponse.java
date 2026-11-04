package com.onehostel.access.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * What sign-in and refresh return: Keycloak's tokens under their snake_case names (the Angular app reads
 * these), plus the privToken of the roles in the access token.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record AuthTokenResponse(
        @JsonProperty("access_token") String accessToken,
        @JsonProperty("expires_in") Long expiresIn,
        @JsonProperty("refresh_token") String refreshToken,
        @JsonProperty("refresh_expires_in") Long refreshExpiresIn,
        @JsonProperty("token_type") String tokenType,
        @JsonProperty("session_state") String sessionState,
        @JsonProperty("scope") String scope,
        @JsonProperty("priv_token") String privToken) {

    public static AuthTokenResponse of(KeycloakTokenResponse tokens, String privToken) {
        return new AuthTokenResponse(tokens.accessToken(), tokens.expiresIn(), tokens.refreshToken(),
                tokens.refreshExpiresIn(), tokens.tokenType(), tokens.sessionState(), tokens.scope(), privToken);
    }
}
