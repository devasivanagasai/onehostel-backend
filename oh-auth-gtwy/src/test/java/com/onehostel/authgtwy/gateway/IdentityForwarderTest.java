package com.onehostel.authgtwy.gateway;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.converter.StringHttpMessageConverter;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.servlet.function.ServerRequest;

import com.onehostel.authgtwy.config.AppProperties;
import com.onehostel.common.constants.CommonEnumHelper.Role;
import com.onehostel.common.security.AuthenticatedUser;
import com.onehostel.common.security.GatewayIdentity;

class IdentityForwarderTest {

    private static final String SECRET = "test-signing-secret";
    private static final AuthenticatedUser TENANT =
            new AuthenticatedUser("t1", "priya@x.com", Role.HOSTEL_TENANT, "org-1", "h1", "bed-1");

    private final IdentityForwarder forwarder = new IdentityForwarder(new AppProperties(
            new AppProperties.Cors(List.of("http://localhost:4200")),
            new AppProperties.Jwt("s", Duration.ofMinutes(15), Duration.ofDays(7), "i"), null,
            new AppProperties.RateLimit(10),
            new AppProperties.Gateway("http://127.0.0.1:8082", SECRET, Duration.ofSeconds(60), false)));

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void stripsWhatAClientCouldForgeAndSignsTheVerifiedCaller() {
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(TENANT, null, List.of()));
        MockHttpServletRequest incoming = new MockHttpServletRequest("POST", "/api/v1/hostels/getall");
        incoming.addHeader(GatewayIdentity.ROLE, "SUPER_ADMIN");
        incoming.addHeader(GatewayIdentity.ORGANIZATION_ID, "someone-elses-org");
        incoming.addHeader(HttpHeaders.AUTHORIZATION, "Bearer the-users-jwt");
        incoming.addHeader(HttpHeaders.COOKIE, "oh_refresh=the-refresh-token");

        HttpHeaders forwarded = forwarder.apply(request(incoming)).headers().asHttpHeaders();

        assertThat(forwarded.get(GatewayIdentity.ROLE)).containsExactly("HOSTEL_TENANT");
        assertThat(forwarded.get(GatewayIdentity.ORGANIZATION_ID)).containsExactly("org-1");
        assertThat(forwarded.containsKey(HttpHeaders.AUTHORIZATION)).isFalse();
        assertThat(forwarded.containsKey(HttpHeaders.COOKIE)).isFalse();

        var verified = verify(forwarded, "POST", "/api/v1/hostels/getall");
        assertThat(verified).isPresent();
        assertThat(verified.get().user()).isEqualTo(TENANT);
    }

    @Test
    void anAnonymousCallIsForwardedSignedWithNoIdentity() {
        HttpHeaders forwarded = forwarder.apply(request(new MockHttpServletRequest("GET", "/api/v1/ping")))
                .headers().asHttpHeaders();

        var verified = verify(forwarded, "GET", "/api/v1/ping");
        assertThat(verified).isPresent();
        assertThat(verified.get().user()).isNull();
    }

    private static ServerRequest request(MockHttpServletRequest servletRequest) {
        return ServerRequest.create(servletRequest, List.of(new StringHttpMessageConverter()));
    }

    private static java.util.Optional<GatewayIdentity.Verified> verify(HttpHeaders headers, String method, String path) {
        return GatewayIdentity.verify(SECRET.getBytes(StandardCharsets.UTF_8), Duration.ofSeconds(60), method, path,
                headers.getFirst(GatewayIdentity.USER_ID), headers.getFirst(GatewayIdentity.EMAIL),
                headers.getFirst(GatewayIdentity.ROLE), headers.getFirst(GatewayIdentity.ORGANIZATION_ID),
                headers.getFirst(GatewayIdentity.HOSTEL_ID), headers.getFirst(GatewayIdentity.BED_ID),
                headers.getFirst(GatewayIdentity.TIMESTAMP), headers.getFirst(GatewayIdentity.SIGNATURE), Instant.now());
    }
}
