package com.onehostel.authgtwy.gateway;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.function.Function;

import org.springframework.http.HttpHeaders;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.function.ServerRequest;

import com.onehostel.authgtwy.config.AppProperties;
import com.onehostel.common.constants.ServiceConstants;
import com.onehostel.common.security.AuthenticatedUser;
import com.onehostel.common.security.GatewayIdentity;
import com.onehostel.common.web.CorrelationIdFilter;

/**
 * Rewrites a request before it is forwarded downstream:
 * <ul>
 * <li>removes anything a client could use to impersonate someone: identity headers it sent itself,
 * the Authorization header and cookies (the refresh token must never leave the gateway);</li>
 * <li>adds the caller verified by GatewayAuthenticationFilter as signed GatewayIdentity headers;</li>
 * <li>adds the request's correlation id, so downstream logs line up with the gateway's.</li>
 * </ul>
 */
@Component
public class IdentityForwarder implements Function<ServerRequest, ServerRequest> {

    private final byte[] secret;

    public IdentityForwarder(AppProperties properties) {
        this.secret = properties.gateway().signingSecret().getBytes(StandardCharsets.UTF_8);
    }

    @Override
    public ServerRequest apply(ServerRequest request) {
        GatewayIdentity.Signed identity = GatewayIdentity.sign(secret, currentUser(), request.method().name(),
                request.uri().getRawPath(), Instant.now());
        String correlationId = CorrelationIdFilter.current();
        return ServerRequest.from(request).headers(headers -> {
            for (String name : GatewayIdentity.ALL_HEADERS) {
                headers.remove(name);
            }
            headers.remove(HttpHeaders.AUTHORIZATION);
            headers.remove(HttpHeaders.COOKIE);
            headers.set(GatewayIdentity.USER_ID, identity.userId());
            headers.set(GatewayIdentity.EMAIL, identity.email());
            headers.set(GatewayIdentity.ROLE, identity.role());
            headers.set(GatewayIdentity.ORGANIZATION_ID, identity.organizationId());
            headers.set(GatewayIdentity.HOSTEL_ID, identity.hostelId());
            headers.set(GatewayIdentity.BED_ID, identity.bedId());
            headers.set(GatewayIdentity.TIMESTAMP, Long.toString(identity.timestamp()));
            headers.set(GatewayIdentity.SIGNATURE, identity.signature());
            if (correlationId != null) {
                headers.set(ServiceConstants.CORRELATION_ID_HEADER, correlationId);
            }
        }).build();
    }

    private static AuthenticatedUser currentUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        return authentication != null && authentication.getPrincipal() instanceof AuthenticatedUser user ? user : null;
    }
}
