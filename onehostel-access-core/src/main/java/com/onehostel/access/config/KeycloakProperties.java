package com.onehostel.access.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import jakarta.validation.constraints.NotBlank;

/**
 * keycloak.*
 *
 * @param clientId            the client password logins use (onehostel-backend)
 * @param serviceClientId     confidential client for OTP token exchange and the admin API (oh-auth-gtwy-service)
 * @param serviceClientSecret no default on purpose: set KEYCLOAK_SERVICE_CLIENT_SECRET in the environment or .env
 */
@Validated
@ConfigurationProperties("keycloak")
public record KeycloakProperties(
        @NotBlank String serverUrl,
        @NotBlank String realm,
        @NotBlank String clientId,
        String clientSecret,
        @NotBlank String serviceClientId,
        @NotBlank(message = "set KEYCLOAK_SERVICE_CLIENT_SECRET (see .env.example)") String serviceClientSecret) {

    public String tokenEndpoint() {
        return realmUrl() + "/protocol/openid-connect/token";
    }

    public String revokeEndpoint() {
        return realmUrl() + "/protocol/openid-connect/revoke";
    }

    public String logoutEndpoint() {
        return realmUrl() + "/protocol/openid-connect/logout";
    }

    public String adminUrl() {
        return serverUrl + "/admin/realms/" + realm;
    }

    private String realmUrl() {
        return serverUrl + "/realms/" + realm;
    }
}
