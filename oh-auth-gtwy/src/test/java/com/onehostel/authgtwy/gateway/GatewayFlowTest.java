package com.onehostel.authgtwy.gateway;

import static org.hamcrest.Matchers.hasItem;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import com.onehostel.authgtwy.model.User;
import com.onehostel.authgtwy.repository.UserRepository;
import com.onehostel.authgtwy.security.JwtService;
import com.onehostel.common.constants.CommonEnumHelper.Role;
import com.onehostel.common.constants.CommonEnumHelper.UserStatus;

/**
 * Requests through the gateway's real security chain. In tests oh-access-core is unreachable, so
 * a routed request answers 503 UPSTREAM_UNAVAILABLE: that status proves the gateway accepted and
 * forwarded it, while 401/403 prove it was stopped at the gateway.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles({ "dev", "test" })
class GatewayFlowTest {

    private static final String ANGULAR = "http://localhost:4200";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtService jwtService;

    @MockitoBean
    private UserRepository userRepository;

    @MockitoBean
    private PublicEndpointRepository publicEndpointRepository;

    @Test
    void anAllowedRequestIsRoutedDownstream() throws Exception {
        String owner = token("flow-owner", Role.HOSTEL_OWNER, true);

        mockMvc.perform(call(post("/api/v1/hostels/create"), owner))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.errorCode").value("UPSTREAM_UNAVAILABLE"))
                .andExpect(jsonPath("$.correlationId").isNotEmpty())
                .andExpect(header().string("Access-Control-Allow-Origin", ANGULAR));
    }

    @Test
    void aRoleWithoutThePermissionIsStoppedAtTheGateway() throws Exception {
        String tenant = token("flow-tenant", Role.HOSTEL_TENANT, true);

        mockMvc.perform(call(post("/api/v1/hostels/create"), tenant))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errorCode").value("FORBIDDEN"))
                .andExpect(header().string("Access-Control-Allow-Origin", ANGULAR));
        // Reading is allowed for tenants.
        mockMvc.perform(call(post("/api/v1/hostels/getall"), tenant)).andExpect(status().isServiceUnavailable());
    }

    @Test
    void anEndpointMissingFromThePrivilegeMapIsRefused() throws Exception {
        String admin = token("flow-admin-1", Role.SUPER_ADMIN, true);

        mockMvc.perform(call(post("/api/v1/rooms/create"), admin))
                .andExpect(status().isForbidden());
    }

    @Test
    void protectedRequestsNeedAValidTokenOfAnActiveAccount() throws Exception {
        mockMvc.perform(call(post("/api/v1/hostels/getall"), null))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.errorCode").value("UNAUTHORIZED"))
                .andExpect(header().string("Access-Control-Allow-Origin", ANGULAR));

        mockMvc.perform(call(post("/api/v1/hostels/getall"), "not-a-jwt"))
                .andExpect(status().isUnauthorized());

        String disabled = token("flow-disabled", Role.HOSTEL_OWNER, false);
        mockMvc.perform(call(post("/api/v1/hostels/getall"), disabled))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void publicEndpointsAreManagedAtRuntimeBySuperAdminsOnly() throws Exception {
        String admin = token("flow-admin-2", Role.SUPER_ADMIN, true);
        String owner = token("flow-owner-2", Role.HOSTEL_OWNER, true);

        mockMvc.perform(call(get("/api/v1/gateway/public-endpoints"), owner)).andExpect(status().isForbidden());
        mockMvc.perform(call(get("/api/v1/gateway/public-endpoints"), admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.responseObject").value(hasItem("POST /api/v1/auth/login")));

        // Not public yet: needs a token.
        mockMvc.perform(call(get("/api/v1/ping"), null)).andExpect(status().isUnauthorized());

        when(publicEndpointRepository.findByEnabledTrue())
                .thenReturn(List.of(new PublicEndpoint("/api/v1/ping", "GET", "Ping", false)));
        mockMvc.perform(call(post("/api/v1/gateway/public-endpoints/refresh"), admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.responseObject").value(hasItem("GET /api/v1/ping")));

        // Public now: routed without a token.
        mockMvc.perform(call(get("/api/v1/ping"), null)).andExpect(status().isServiceUnavailable());
    }

    private String token(String id, Role role, boolean active) {
        User user = new User("Flow " + role, id + "@x.com", "hash", role);
        user.setId(id);
        user.setOrganizationId("org-flow");
        if (!active) {
            user.setStatus(UserStatus.INACTIVE);
        }
        when(userRepository.findById(id)).thenReturn(Optional.of(user));
        return jwtService.issue(user).token();
    }

    private static MockHttpServletRequestBuilder call(MockHttpServletRequestBuilder request, String token) {
        request.header("Origin", ANGULAR).contentType(MediaType.APPLICATION_JSON).content("{ \"requestObject\": {} }");
        return token == null ? request : request.header("Authorization", "Bearer " + token);
    }
}
