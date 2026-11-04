package com.onehostel.common.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * app.internal.*
 *
 * @param secret        shared with the gateway (env GATEWAY_INTERNAL_SECRET); proves a request came through it
 * @param verifyInbound true on every service; false on the gateway, which sends the secret rather than checks it
 */
@ConfigurationProperties("app.internal")
public record InternalProperties(String secret, @DefaultValue("true") boolean verifyInbound) {

    public boolean hasSecret() {
        return secret != null && !secret.isBlank();
    }
}
