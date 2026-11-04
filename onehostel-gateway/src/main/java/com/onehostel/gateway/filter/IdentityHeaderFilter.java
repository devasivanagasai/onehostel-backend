package com.onehostel.gateway.filter;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.filter.OncePerRequestFilter;

import com.onehostel.common.exception.CommonErrorCode;
import com.onehostel.common.exception.ErrorResponseWriter;
import com.onehostel.common.helper.CorrelationContext;
import com.onehostel.gateway.config.SecurityConfig;
import com.onehostel.gateway.helper.GatewayIdentityRequest;
import com.onehostel.tenancy.constants.IdentityHeaders;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Runs after authentication. Strips every identity header the client sent and sets them again from the
 * verified access token, plus the client IP, the correlation id and the internal secret, so downstream
 * services can trust what they receive. A client may name an organization in X-Organization-Id only if
 * it is its own (any organization for SUPER_ADMIN); anything else is refused here.
 */
public class IdentityHeaderFilter extends OncePerRequestFilter {

    /** Token claims set by the realm's protocol mappers. */
    static final String CLAIM_USER_ID = "user_id";
    static final String CLAIM_ORGANIZATION = "organization_id";
    static final String CLAIM_PARENT_ORGS = "parent_org_ids";
    static final String CLAIM_SESSION = "sid";

    private final String internalSecret;
    private final ErrorResponseWriter errorWriter;

    public IdentityHeaderFilter(String internalSecret, ErrorResponseWriter errorWriter) {
        this.internalSecret = internalSecret;
        this.errorWriter = errorWriter;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {

        List<String> hidden = new ArrayList<>(IdentityHeaders.ALL);
        hidden.add(CorrelationContext.HEADER);
        GatewayIdentityRequest forwarded = new GatewayIdentityRequest(request, hidden)
                .with(IdentityHeaders.CLIENT_IP, request.getRemoteAddr())
                .with(CorrelationContext.HEADER, CorrelationContext.current())
                .with(IdentityHeaders.INTERNAL_SECRET, internalSecret);

        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication instanceof JwtAuthenticationToken token) {
            Jwt jwt = token.getToken();
            List<String> roles = jwt.hasClaim(SecurityConfig.ROLES_CLAIM) ? jwt.getClaimAsStringList(SecurityConfig.ROLES_CLAIM) : List.of();
            String tokenOrganization = jwt.getClaimAsString(CLAIM_ORGANIZATION);
            String requestedOrganization = request.getHeader(IdentityHeaders.ORGANIZATION_ID);
            boolean superAdmin = roles.contains(IdentityHeaders.SUPER_ADMIN_ROLE);

            String organization = tokenOrganization;
            if (requestedOrganization != null && !requestedOrganization.isBlank()) {
                if (!superAdmin && !requestedOrganization.trim().equals(tokenOrganization)) {
                    errorWriter.write(response, CommonErrorCode.SECURITY_BREACH, CommonErrorCode.SECURITY_BREACH.message());
                    return;
                }
                organization = requestedOrganization.trim();
            }
            // A super admin acting for an organization takes no parent chain from their own token
            List<String> parents = organization != null && organization.equals(tokenOrganization)
                    && jwt.hasClaim(CLAIM_PARENT_ORGS) ? jwt.getClaimAsStringList(CLAIM_PARENT_ORGS) : List.of();

            forwarded.with(IdentityHeaders.USER_ID, jwt.hasClaim(CLAIM_USER_ID) ? jwt.getClaimAsString(CLAIM_USER_ID) : jwt.getSubject())
                    .with(IdentityHeaders.USERNAME, jwt.getClaimAsString("preferred_username"))
                    .with(IdentityHeaders.USER_EMAIL, jwt.getClaimAsString("email"))
                    .with(IdentityHeaders.USER_ROLES, String.join(",", roles))
                    .with(IdentityHeaders.ORGANIZATION_ID, organization)
                    .with(IdentityHeaders.PARENT_ORG_IDS, String.join(",", parents))
                    .with(IdentityHeaders.AUTH_SUBJECT, jwt.getSubject())
                    .with(IdentityHeaders.AUTH_SESSION, jwt.getClaimAsString(CLAIM_SESSION));
        }
        chain.doFilter(forwarded, response);
    }
}
