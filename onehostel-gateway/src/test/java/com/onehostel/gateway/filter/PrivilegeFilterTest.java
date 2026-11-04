package com.onehostel.gateway.filter;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

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
import com.onehostel.gateway.dto.PrivilegeMatrix;
import com.onehostel.gateway.dto.PrivilegeMatrix.Api;
import com.onehostel.gateway.dto.PrivilegeMatrix.Entry;

class PrivilegeFilterTest {

    /** Bit 1: ROOMS_VIEW (reads), bit 2: ROOMS_MANAGE (everything under property), bit 3: ROLE_MANAGE. */
    private static final PrivilegeMatrix MATRIX = new PrivilegeMatrix(
            List.of(
                    new Entry("ROOMS_VIEW", 1, List.of(new Api("GET", "/api/v1/property/**"), new Api("POST", "/api/v1/property/**/getall"))),
                    new Entry("ROOMS_MANAGE", 2, List.of(new Api("*", "/api/v1/property/**"))),
                    new Entry("ROLE_MANAGE", 3, List.of(new Api("*", "/api/v1/access/roles/**")))),
            Map.of(
                    "HOSTEL_OWNER", "110",
                    "HOSTEL_MANAGER", "100",
                    "SUPER_ADMIN", "001",
                    // ROOMS_MANAGE's position present but '0': must deny
                    "AUDITOR", "10"));

    private Optional<PrivilegeMatrix> matrix = Optional.of(MATRIX);
    private final PrivilegeFilter filter = new PrivilegeFilter(() -> matrix,
            List.of("/api/v1/auth/login", "/api/v1/access/privileges"),
            new ErrorResponseWriter(new ObjectMapper().findAndRegisterModules(), "onehostel-gateway"));

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    private static void signIn(String... roles) {
        Jwt jwt = Jwt.withTokenValue("token").header("alg", "RS256").subject("kc-1")
                .claim("roles", List.of(roles))
                .issuedAt(Instant.now()).expiresAt(Instant.now().plusSeconds(300))
                .build();
        SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(jwt));
    }

    /** The response status, or 0 when the call was passed on. */
    private int call(String method, String path) throws Exception {
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();
        filter.doFilter(new MockHttpServletRequest(method, path), response, chain);
        return chain.getRequest() != null ? 0 : response.getStatus();
    }

    @Test
    void ownerMayChangeRooms() throws Exception {
        signIn("HOSTEL_OWNER");
        assertThat(call("POST", "/api/v1/property/room/create")).isZero();
        assertThat(call("DELETE", "/api/v1/property/room/delete/01J")).isZero();
    }

    @Test
    void managerWithViewOnlyMayReadButNotChange() throws Exception {
        signIn("HOSTEL_MANAGER");
        assertThat(call("GET", "/api/v1/property/room/inq/01J")).isZero();
        assertThat(call("POST", "/api/v1/property/room/getall")).isZero();
        assertThat(call("POST", "/api/v1/property/room/create")).isEqualTo(403);
        assertThat(call("PUT", "/api/v1/property/room/update/01J")).isEqualTo(403);
    }

    @Test
    void aZeroBitDenies() throws Exception {
        signIn("AUDITOR");
        assertThat(call("POST", "/api/v1/property/room/create")).isEqualTo(403);
    }

    @Test
    void superAdminHasNoHostelModules() throws Exception {
        signIn("SUPER_ADMIN");
        assertThat(call("GET", "/api/v1/property/room/inq/01J")).isEqualTo(403);
        assertThat(call("PUT", "/api/v1/access/roles/HOSTEL_MANAGER/privileges")).isZero();
    }

    @Test
    void severalRolesHoldTheUnionOfTheirBits() throws Exception {
        signIn("HOSTEL_MANAGER", "SUPER_ADMIN");
        assertThat(call("GET", "/api/v1/property/room/inq/01J")).isZero();
        assertThat(call("PUT", "/api/v1/access/roles/HOSTEL_MANAGER/privileges")).isZero();
    }

    @Test
    void unknownRolesAndNoRolesAreDenied() throws Exception {
        signIn("offline_access");
        assertThat(call("GET", "/api/v1/property/room/inq/01J")).isEqualTo(403);
        SecurityContextHolder.clearContext();
        assertThat(call("GET", "/api/v1/property/room/inq/01J")).isEqualTo(403);
    }

    @Test
    void anEndpointNoPrivilegeCoversIsDenied() throws Exception {
        signIn("HOSTEL_OWNER", "SUPER_ADMIN");
        assertThat(call("GET", "/api/v1/billing/invoice/inq/01J")).isEqualTo(403);
    }

    @Test
    void unrestrictedAndNonApiPathsAreNotChecked() throws Exception {
        signIn("offline_access");
        assertThat(call("GET", "/api/v1/access/privileges")).isZero();
        assertThat(call("POST", "/api/v1/auth/login")).isZero();
        assertThat(call("GET", "/actuator/health")).isZero();
        assertThat(call("OPTIONS", "/api/v1/property/room/create")).isZero();
    }

    @Test
    void withoutAMatrixEveryApiCallIsRefused() throws Exception {
        matrix = Optional.empty();
        signIn("HOSTEL_OWNER");
        assertThat(call("GET", "/api/v1/property/room/inq/01J")).isEqualTo(503);
    }
}
