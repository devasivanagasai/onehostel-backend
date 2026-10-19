package com.onehostel.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasItem;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

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

import com.onehostel.constants.CommonEnumHelper.Role;
import com.onehostel.constants.CommonEnumHelper.Status;
import com.onehostel.dto.helper.CommonRequestObject;
import com.onehostel.dto.helper.ListFilter;
import com.onehostel.dto.helper.PageResponse;
import com.onehostel.dto.hostel.HostelRequest;
import com.onehostel.dto.hostel.HostelView;
import com.onehostel.model.User;
import com.onehostel.security.AuthenticatedUser;
import com.onehostel.security.JwtService;
import com.onehostel.service.HostelService;

/**
 * Wiring of the generic HostelCoreController through HostelController: the payload type is
 * resolved from the subclass (so validation runs on HostelRequest), the principal carries the
 * token's organization, and responses use the standard envelope.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles({ "dev", "test" })
class HostelControllerTest {

    private static final String VALID_HOSTEL = """
            { "requestObject": { "name": "Sunrise", "addressLine": "12 MG Road", "city": "Bengaluru",
              "state": "Karnataka", "pincode": "560001" } }""";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtService jwtService;

    @MockitoBean
    private HostelService hostelService;

    private String ownerToken;

    @BeforeEach
    void setUp() {
        User owner = new User("Owner", "owner@x.com", "hash", Role.HOSTEL_OWNER);
        owner.setId("o1");
        owner.setOrganizationId("org-a");
        ownerToken = jwtService.issue(owner).token();
        when(hostelService.entityLabel()).thenReturn("Hostel");
    }

    @Test
    void invalidPayloadIsValidatedAsHostelRequest() throws Exception {
        mockMvc.perform(call("/api/v1/hostels/create", "{ \"requestObject\": { \"name\": \"\", \"pincode\": \"0123\" } }"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.errors[*].field").value(hasItem("requestObject.name")))
                .andExpect(jsonPath("$.errors[*].field").value(hasItem("requestObject.pincode")));
    }

    @Test
    void createReturns201WithTheTokensOrganization() throws Exception {
        when(hostelService.create(any(), any())).thenReturn(view());

        mockMvc.perform(call("/api/v1/hostels/create", VALID_HOSTEL))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.statusCode").value(201))
                .andExpect(jsonPath("$.message").value("Hostel created successfully."))
                .andExpect(jsonPath("$.responseObject.id").value("h1"));

        ArgumentCaptor<HostelRequest> dto = ArgumentCaptor.forClass(HostelRequest.class);
        ArgumentCaptor<AuthenticatedUser> user = ArgumentCaptor.forClass(AuthenticatedUser.class);
        verify(hostelService).create(dto.capture(), user.capture());
        assertThat(dto.getValue().name()).isEqualTo("Sunrise");
        assertThat(user.getValue().organizationId()).isEqualTo("org-a");
    }

    @Test
    @SuppressWarnings("unchecked")
    void getAllPassesFilterAndPaging() throws Exception {
        when(hostelService.getAll(any(), any())).thenReturn(PageResponse.of(java.util.List.of(view()), 1, 5, 6));

        mockMvc.perform(call("/api/v1/hostels/getall",
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
        mockMvc.perform(call("/api/v1/hostels/inq", "{ \"requestObject\": { } }"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("requestObject.id"));
    }

    @Test
    void callsWithoutATokenAreRejected() throws Exception {
        mockMvc.perform(post("/api/v1/hostels/getall").contentType(MediaType.APPLICATION_JSON)
                .content("{ \"requestObject\": { } }"))
                .andExpect(status().isUnauthorized());
    }

    private MockHttpServletRequestBuilder call(String path, String body) {
        return post(path).header("Authorization", "Bearer " + ownerToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body);
    }

    private static HostelView view() {
        return new HostelView("h1", "org-a", "Sunrise", "12 MG Road", "Bengaluru", "Karnataka", "560001", null,
                Status.ACTIVE, 0L, null, null);
    }
}
