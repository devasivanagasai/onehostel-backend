package com.onehostel.accesscore.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasItem;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import com.onehostel.accesscore.config.CoreProperties;
import com.onehostel.accesscore.dto.hostel.HostelRequest;
import com.onehostel.accesscore.dto.hostel.HostelView;
import com.onehostel.accesscore.service.HostelService;
import com.onehostel.common.constants.CommonEnumHelper.Role;
import com.onehostel.common.constants.CommonEnumHelper.Status;
import com.onehostel.common.dto.CommonRequestObject;
import com.onehostel.common.dto.ListFilter;
import com.onehostel.common.dto.PageResponse;
import com.onehostel.common.security.AuthenticatedUser;
import com.onehostel.common.security.GatewayIdentity;

/**
 * oh-access-core behind the gateway: requests carry the caller as signed GatewayIdentity headers
 * (what oh-auth-gtwy sends). Anything unsigned, forged or stale is refused, and the generic
 * HostelCoreController still validates HostelRequest and passes the caller's organization on.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles({ "dev", "test" })
class HostelControllerTest {

    private static final String VALID_HOSTEL = """
            { "requestObject": { "name": "Sunrise", "addressLine": "12 MG Road", "city": "Bengaluru",
              "state": "Karnataka", "pincode": "560001" } }""";

    private static final AuthenticatedUser OWNER =
            new AuthenticatedUser("o1", "owner@x.com", Role.HOSTEL_OWNER, "org-a", null, null);

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private CoreProperties properties;

    @MockitoBean
    private HostelService hostelService;

    @BeforeEach
    void setUp() {
        when(hostelService.entityLabel()).thenReturn("Hostel");
    }

    @Test
    void invalidPayloadIsValidatedAsHostelRequest() throws Exception {
        mockMvc.perform(signed("/api/v1/hostels/create", "{ \"requestObject\": { \"name\": \"\", \"pincode\": \"0123\" } }"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.errors[*].field").value(hasItem("requestObject.name")))
                .andExpect(jsonPath("$.errors[*].field").value(hasItem("requestObject.pincode")));
    }

    @Test
    void createReturns201WithTheCallersOrganization() throws Exception {
        when(hostelService.create(any(), any())).thenReturn(view());

        mockMvc.perform(signed("/api/v1/hostels/create", VALID_HOSTEL))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.statusCode").value(201))
                .andExpect(jsonPath("$.message").value("Hostel created successfully."))
                .andExpect(jsonPath("$.responseObject.id").value("h1"));

        ArgumentCaptor<HostelRequest> dto = ArgumentCaptor.forClass(HostelRequest.class);
        ArgumentCaptor<AuthenticatedUser> user = ArgumentCaptor.forClass(AuthenticatedUser.class);
        verify(hostelService).create(dto.capture(), user.capture());
        assertThat(dto.getValue().name()).isEqualTo("Sunrise");
        assertThat(user.getValue()).isEqualTo(OWNER);
    }

    @Test
    @SuppressWarnings("unchecked")
    void getAllPassesFilterAndPaging() throws Exception {
        when(hostelService.getAll(any(), any())).thenReturn(PageResponse.of(List.of(view()), 1, 5, 6));

        mockMvc.perform(signed("/api/v1/hostels/getall",
                "{ \"requestObject\": { \"keyword\": \"sun\" }, \"page\": 1, \"size\": 5, \"sortField\": \"name\" }"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.responseObject.content[0].name").value("Sunrise"))
                .andExpect(jsonPath("$.responseObject.totalPages").value(2));

        ArgumentCaptor<CommonRequestObject<ListFilter>> request = ArgumentCaptor.forClass(CommonRequestObject.class);
        verify(hostelService).getAll(request.capture(), any());
        assertThat(request.getValue().requestObject().keyword()).isEqualTo("sun");
        assertThat(request.getValue().page()).isEqualTo(1);
    }

    @Test
    void inquireRequiresAnId() throws Exception {
        mockMvc.perform(signed("/api/v1/hostels/inq", "{ \"requestObject\": { } }"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("requestObject.id"));
    }

    // --- only the gateway gets in ---

    @Test
    void requestsThatDidNotComeThroughTheGatewayAreRejected() throws Exception {
        mockMvc.perform(json("/api/v1/hostels/getall", "{ \"requestObject\": { } }"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.errorCode").value("UNAUTHORIZED"));

        // A client that sets identity headers itself, without the gateway secret.
        mockMvc.perform(json("/api/v1/hostels/getall", "{ \"requestObject\": { } }")
                .header(GatewayIdentity.USER_ID, "o1").header(GatewayIdentity.ROLE, "SUPER_ADMIN")
                .header(GatewayIdentity.TIMESTAMP, Instant.now().getEpochSecond())
                .header(GatewayIdentity.SIGNATURE, "00ff"))
                .andExpect(status().isUnauthorized());

        // A raw JWT is not accepted here: only the gateway validates tokens.
        mockMvc.perform(json("/api/v1/hostels/getall", "{ \"requestObject\": { } }")
                .header("Authorization", "Bearer eyJhbGciOiJIUzI1NiJ9.e30.x"))
                .andExpect(status().isUnauthorized());
        verify(hostelService, never()).getAll(any(), any());
    }

    @Test
    void aSignatureForOneUserCannotBeReusedForAnother() throws Exception {
        GatewayIdentity.Signed owner = sign(OWNER, "/api/v1/hostels/getall", Instant.now());
        // Same signature, but the role changed to SUPER_ADMIN.
        GatewayIdentity.Signed forged = new GatewayIdentity.Signed(owner.userId(), owner.email(), "SUPER_ADMIN",
                owner.organizationId(), owner.hostelId(), owner.bedId(), owner.timestamp(), owner.signature());

        mockMvc.perform(withIdentity(json("/api/v1/hostels/getall", "{ \"requestObject\": { } }"), forged))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void aSignatureForOnePathCannotBeReusedForAnother() throws Exception {
        GatewayIdentity.Signed identity = sign(OWNER, "/api/v1/hostels/inq", Instant.now());

        mockMvc.perform(withIdentity(json("/api/v1/hostels/delete", "{ \"requestObject\": { \"id\": \"h1\" } }"), identity))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void staleSignaturesAreRejected() throws Exception {
        GatewayIdentity.Signed identity = sign(OWNER, "/api/v1/hostels/getall", Instant.now().minusSeconds(120));

        mockMvc.perform(withIdentity(json("/api/v1/hostels/getall", "{ \"requestObject\": { } }"), identity))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void roleAreasAreEnforcedHereToo() throws Exception {
        GatewayIdentity.Signed identity = sign(OWNER, "/api/v1/admin/anything", Instant.now());

        mockMvc.perform(withIdentity(json("/api/v1/admin/anything", "{}"), identity))
                .andExpect(status().isForbidden());
    }

    private MockHttpServletRequestBuilder signed(String path, String body) {
        return withIdentity(json(path, body), sign(OWNER, path, Instant.now()));
    }

    private GatewayIdentity.Signed sign(AuthenticatedUser user, String path, Instant at) {
        return GatewayIdentity.sign(properties.gateway().signingSecret().getBytes(StandardCharsets.UTF_8), user,
                "POST", path, at);
    }

    private static MockHttpServletRequestBuilder withIdentity(MockHttpServletRequestBuilder request,
            GatewayIdentity.Signed identity) {
        return request.header(GatewayIdentity.USER_ID, identity.userId())
                .header(GatewayIdentity.EMAIL, identity.email())
                .header(GatewayIdentity.ROLE, identity.role())
                .header(GatewayIdentity.ORGANIZATION_ID, identity.organizationId())
                .header(GatewayIdentity.HOSTEL_ID, identity.hostelId())
                .header(GatewayIdentity.BED_ID, identity.bedId())
                .header(GatewayIdentity.TIMESTAMP, identity.timestamp())
                .header(GatewayIdentity.SIGNATURE, identity.signature());
    }

    private static MockHttpServletRequestBuilder json(String path, String body) {
        return post(path).contentType(MediaType.APPLICATION_JSON).content(body);
    }

    private static HostelView view() {
        return new HostelView("h1", "org-a", "Sunrise", "12 MG Road", "Bengaluru", "Karnataka", "560001", null,
                Status.ACTIVE, 0L, null, null);
    }
}
