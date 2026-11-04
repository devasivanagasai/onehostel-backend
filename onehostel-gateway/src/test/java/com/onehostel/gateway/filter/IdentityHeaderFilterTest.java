package com.onehostel.gateway.filter;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.Collections;
import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.onehostel.common.exception.ErrorResponseWriter;
import com.onehostel.tenancy.constants.IdentityHeaders;

import jakarta.servlet.http.HttpServletRequest;

class IdentityHeaderFilterTest {

    private final IdentityHeaderFilter filter = new IdentityHeaderFilter("s3cret",
            new ErrorResponseWriter(new ObjectMapper().findAndRegisterModules(), "onehostel-gateway"));

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    private static void signIn(String organization, String... roles) {
        Jwt jwt = Jwt.withTokenValue("token").header("alg", "RS256")
                .subject("kc-123")
                .claim("user_id", "01JUSER")
                .claim("preferred_username", "rajesh.kumar")
                .claim("email", "rajesh.kumar@onehostel.com")
                .claim("roles", List.of(roles))
                .claim("organization_id", organization)
                .claim("sid", "session-9")
                .issuedAt(Instant.now()).expiresAt(Instant.now().plusSeconds(300))
                .build();
        SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(jwt));
    }

    private static MockHttpServletRequest request() {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/property/room/inq/1");
        request.setRemoteAddr("203.0.113.7");
        return request;
    }

    private HttpServletRequest forward(MockHttpServletRequest request, MockHttpServletResponse response) throws Exception {
        MockFilterChain chain = new MockFilterChain();
        filter.doFilter(request, response, chain);
        return (HttpServletRequest) chain.getRequest();
    }

    @Test
    void forgedIdentityHeadersAreReplacedByTokenValues() throws Exception {
        signIn("org-sunrise", "HOSTEL_OWNER");
        MockHttpServletRequest request = request();
        request.addHeader(IdentityHeaders.USER_ID, "someone-else");
        request.addHeader(IdentityHeaders.USER_ROLES, "SUPER_ADMIN");
        request.addHeader(IdentityHeaders.INTERNAL_SECRET, "guess");
        request.addHeader(IdentityHeaders.CLIENT_IP, "10.0.0.1");

        HttpServletRequest forwarded = forward(request, new MockHttpServletResponse());

        assertThat(forwarded.getHeader(IdentityHeaders.USER_ID)).isEqualTo("01JUSER");
        assertThat(forwarded.getHeader(IdentityHeaders.USER_ROLES)).isEqualTo("HOSTEL_OWNER");
        assertThat(forwarded.getHeader(IdentityHeaders.ORGANIZATION_ID)).isEqualTo("org-sunrise");
        assertThat(forwarded.getHeader(IdentityHeaders.AUTH_SUBJECT)).isEqualTo("kc-123");
        assertThat(forwarded.getHeader(IdentityHeaders.AUTH_SESSION)).isEqualTo("session-9");
        assertThat(forwarded.getHeader(IdentityHeaders.INTERNAL_SECRET)).isEqualTo("s3cret");
        assertThat(forwarded.getHeader(IdentityHeaders.CLIENT_IP)).isEqualTo("203.0.113.7");
        assertThat(Collections.list(forwarded.getHeaders(IdentityHeaders.USER_ID))).containsExactly("01JUSER");
    }

    @Test
    void anonymousRequestsCarryNoIdentity() throws Exception {
        MockHttpServletRequest request = request();
        request.addHeader(IdentityHeaders.USER_ID, "forged");
        request.addHeader(IdentityHeaders.ORGANIZATION_ID, "org-other");

        HttpServletRequest forwarded = forward(request, new MockHttpServletResponse());

        assertThat(forwarded.getHeader(IdentityHeaders.USER_ID)).isNull();
        assertThat(forwarded.getHeader(IdentityHeaders.ORGANIZATION_ID)).isNull();
        assertThat(Collections.list(forwarded.getHeaderNames())).doesNotContain(IdentityHeaders.USER_ID);
    }

    @Test
    void anotherOrganizationIsRefusedForANormalUser() throws Exception {
        signIn("org-sunrise", "HOSTEL_OWNER");
        MockHttpServletRequest request = request();
        request.addHeader(IdentityHeaders.ORGANIZATION_ID, "org-other");
        MockHttpServletResponse response = new MockHttpServletResponse();

        MockFilterChain chain = new MockFilterChain();
        filter.doFilter(request, response, chain);

        assertThat(chain.getRequest()).isNull();
        assertThat(response.getStatus()).isEqualTo(403);
        assertThat(response.getContentAsString()).contains("\"errorCode\":\"SECURITY_BREACH\"");
    }

    @Test
    void superAdminMayActForAnyOrganization() throws Exception {
        signIn(null, "SUPER_ADMIN");
        MockHttpServletRequest request = request();
        request.addHeader(IdentityHeaders.ORGANIZATION_ID, "org-other");

        HttpServletRequest forwarded = forward(request, new MockHttpServletResponse());

        assertThat(forwarded.getHeader(IdentityHeaders.ORGANIZATION_ID)).isEqualTo("org-other");
        assertThat(forwarded.getHeader(IdentityHeaders.USER_ROLES)).isEqualTo("SUPER_ADMIN");
    }
}
