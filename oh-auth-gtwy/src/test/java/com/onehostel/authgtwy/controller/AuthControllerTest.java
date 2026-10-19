package com.onehostel.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.allOf;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasItem;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Duration;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.RequestBuilder;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.onehostel.constants.CommonEnumHelper.Role;
import com.onehostel.constants.CommonEnumHelper.UserStatus;
import com.onehostel.dto.auth.OtpSendResponse;
import com.onehostel.exception.ErrorCode;
import com.onehostel.model.User;
import com.onehostel.repository.UserRepository;
import com.onehostel.service.RefreshTokenService;
import com.onehostel.service.otp.OtpService;

import jakarta.servlet.http.Cookie;

/**
 * The single /login in every mode, account states, the refresh cookie, and role areas.
 * Repositories and the token/OTP stores are mocked, so no MongoDB server is needed; OtpService
 * and RefreshTokenService have their own unit tests.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles({ "dev", "test" })
class AuthControllerTest {

    private static final String PASSWORD = "Correct-Horse-9";
    private static final String PASSCODE = "482910";
    private static final String AUTH = "/api/v1/auth";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @MockitoBean
    private UserRepository userRepository;

    @MockitoBean
    private RefreshTokenService refreshTokenService;

    @MockitoBean
    private OtpService otpService;

    @BeforeEach
    void setUp() {
        when(userRepository.findByEmail(anyString())).thenReturn(Optional.empty());
        when(userRepository.findByPhoneNumber(anyString())).thenReturn(Optional.empty());
        when(userRepository.findByUsername(anyString())).thenReturn(Optional.empty());
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(refreshTokenService.issue(anyString(), any())).thenReturn("refresh-1");
        when(refreshTokenService.ttl()).thenReturn(Duration.ofDays(7));
    }

    @Test
    void superAdminLogsInWithPasswordByEmailOrUsername() throws Exception {
        User admin = givenUser("a1", "admin@onehostel.com", null, Role.SUPER_ADMIN);
        admin.setUsername("root");
        when(userRepository.findByUsername("root")).thenReturn(Optional.of(admin));

        mockMvc.perform(login("Admin@OneHostel.com", PASSWORD, "PASSWORD", "SUPER_ADMIN")).andExpect(status().isOk());
        mockMvc.perform(login("root", PASSWORD, "PASSWORD", "SUPER_ADMIN"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Authentication successful."))
                .andExpect(jsonPath("$.responseObject.userProfile.role").value("SUPER_ADMIN"))
                .andExpect(jsonPath("$.responseObject.userProfile.organizationId").doesNotExist());
    }

    @Test
    void superAdminLogsInWithPasscode() throws Exception {
        givenUser("a1", "admin@onehostel.com", null, Role.SUPER_ADMIN).setPasscodeHash(passwordEncoder.encode(PASSCODE));

        mockMvc.perform(login("admin@onehostel.com", PASSCODE, "PASSCODE", "SUPER_ADMIN")).andExpect(status().isOk());
        mockMvc.perform(login("admin@onehostel.com", "000000", "PASSCODE", "SUPER_ADMIN"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.errorCode").value("INVALID_CREDENTIALS"));
    }

    @Test
    void passcodeIsRefusedWhenNoneIsSet() throws Exception {
        givenUser("a1", "admin@onehostel.com", null, Role.SUPER_ADMIN);

        mockMvc.perform(login("admin@onehostel.com", PASSCODE, "PASSCODE", "SUPER_ADMIN"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void passcodeIsOnlyForSuperAdmins() throws Exception {
        mockMvc.perform(login("9848012345", PASSCODE, "PASSCODE", "HOSTEL_OWNER"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("BAD_REQUEST"));
    }

    @Test
    void ownerLogsInByPhoneAndGetsTheStandardResponse() throws Exception {
        givenUser("o1", "owner@onehostel.com", "9848012345", Role.HOSTEL_OWNER).setOrganizationId("org-a");

        mockMvc.perform(login("9848012345", PASSWORD, "PASSWORD", "HOSTEL_OWNER"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.responseObject.tokenType").value("Bearer"))
                .andExpect(jsonPath("$.responseObject.accessToken").isNotEmpty())
                .andExpect(jsonPath("$.responseObject.expiresIn").value(900))
                .andExpect(jsonPath("$.responseObject.userProfile.userId").value("o1"))
                .andExpect(jsonPath("$.responseObject.userProfile.mobileNumber").value("9848012345"))
                .andExpect(jsonPath("$.responseObject.userProfile.status").value("ACTIVE"))
                .andExpect(jsonPath("$.responseObject.userProfile.organizationId").value("org-a"))
                .andExpect(jsonPath("$.responseObject.userProfile.permissions").value(hasItem("HOSTEL_CREATE")))
                .andExpect(jsonPath("$.responseObject.userProfile.passwordHash").doesNotExist())
                .andExpect(jsonPath("$.responseObject.refreshToken").doesNotExist());
    }

    @Test
    void otpModeLogsInWithTheVerifiedCode() throws Exception {
        User owner = givenUser("o1", "owner@onehostel.com", "9848012345", Role.HOSTEL_OWNER);
        when(otpService.verify("9848012345", "123456", Role.HOSTEL_OWNER)).thenReturn(owner);

        mockMvc.perform(login("9848012345", "123456", "OTP", "HOSTEL_OWNER"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.responseObject.userProfile.role").value("HOSTEL_OWNER"))
                .andExpect(header().string(HttpHeaders.SET_COOKIE, containsString("oh_refresh=refresh-1")));
    }

    @Test
    void wrongOtpIsRejected() throws Exception {
        when(otpService.verify(anyString(), anyString(), eq(Role.HOSTEL_TENANT)))
                .thenThrow(ErrorCode.INVALID_OTP.raise());

        mockMvc.perform(login("9701234567", "999999", "OTP", "HOSTEL_TENANT"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.errorCode").value("INVALID_OTP"));
    }

    @Test
    void loginSetsTheRefreshTokenOnlyAsAStrictHttpOnlyCookie() throws Exception {
        givenUser("t1", "tenant@onehostel.com", null, Role.HOSTEL_TENANT);

        mockMvc.perform(login("tenant@onehostel.com", PASSWORD, "PASSWORD", "HOSTEL_TENANT"))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.SET_COOKIE, allOf(containsString("oh_refresh=refresh-1"),
                        containsString("HttpOnly"), containsString("Secure"), containsString("SameSite=Strict"),
                        containsString("Path=/api/v1/auth"))));
    }

    @Test
    void anAccountOnlyLogsInAsItsOwnUserType() throws Exception {
        givenUser("o1", "owner@onehostel.com", null, Role.HOSTEL_OWNER);

        mockMvc.perform(login("owner@onehostel.com", PASSWORD, "PASSWORD", "HOSTEL_TENANT"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.errorCode").value("INVALID_CREDENTIALS"));
        mockMvc.perform(login("owner@onehostel.com", PASSWORD, "PASSWORD", "SUPER_ADMIN"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void wrongPasswordAndUnknownAccountGetTheSameAnswer() throws Exception {
        givenUser("t1", "tenant@onehostel.com", null, Role.HOSTEL_TENANT);

        String wrong = mockMvc.perform(login("tenant@onehostel.com", "wrong-password", "PASSWORD", "HOSTEL_TENANT"))
                .andExpect(status().isUnauthorized()).andReturn().getResponse().getContentAsString();
        String unknown = mockMvc.perform(login("nobody@onehostel.com", PASSWORD, "PASSWORD", "HOSTEL_TENANT"))
                .andExpect(status().isUnauthorized()).andReturn().getResponse().getContentAsString();

        JsonNode a = objectMapper.readTree(wrong);
        JsonNode b = objectMapper.readTree(unknown);
        assertThat(a.get("errorCode")).isEqualTo(b.get("errorCode"));
        assertThat(a.get("message")).isEqualTo(b.get("message"));
    }

    @Test
    void accountStateIsCheckedAfterTheCredential() throws Exception {
        User tenant = givenUser("t1", "tenant@onehostel.com", null, Role.HOSTEL_TENANT);

        tenant.setLocked(true);
        expectLoginError(423, "ACCOUNT_LOCKED");

        tenant.setLocked(false);
        tenant.setStatus(UserStatus.PENDING_VERIFICATION);
        expectLoginError(403, "ACCOUNT_NOT_VERIFIED");

        tenant.setStatus(UserStatus.BLOCKED);
        expectLoginError(403, "ACCOUNT_DISABLED");

        tenant.setStatus(UserStatus.INACTIVE);
        expectLoginError(403, "ACCOUNT_DISABLED");

        // A wrong password never reveals the state.
        mockMvc.perform(login("tenant@onehostel.com", "wrong-password", "PASSWORD", "HOSTEL_TENANT"))
                .andExpect(jsonPath("$.errorCode").value("INVALID_CREDENTIALS"));
    }

    @Test
    void invalidRequestReturnsFieldErrors() throws Exception {
        mockMvc.perform(post(AUTH + "/login").contentType(MediaType.APPLICATION_JSON)
                .content("{ \"requestObject\": { \"identifier\": \"\", \"secret\": \"\" } }"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[*].field").value(containsInAnyOrder("requestObject.identifier",
                        "requestObject.secret", "requestObject.authMode", "requestObject.userType")));
    }

    @Test
    void otpSendAnswersTheSameWhetherOrNotTheAccountExists() throws Exception {
        when(otpService.send(any())).thenReturn(new OtpSendResponse(300));

        mockMvc.perform(json(AUTH + "/otp/send",
                Map.of("identifier", "9848012345", "channel", "SMS", "userType", "HOSTEL_OWNER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("If an account matches, a code has been sent."))
                .andExpect(jsonPath("$.responseObject.expiresInSeconds").value(300));
    }

    @Test
    void refreshRotatesTheCookieWithinTheSameSession() throws Exception {
        User owner = givenUser("o1", "owner@onehostel.com", null, Role.HOSTEL_OWNER);
        when(userRepository.findById("o1")).thenReturn(Optional.of(owner));
        when(refreshTokenService.consume("refresh-old")).thenReturn(new RefreshTokenService.Rotation("o1", "fam-1"));
        when(refreshTokenService.issue("o1", "fam-1")).thenReturn("refresh-new");

        mockMvc.perform(post(AUTH + "/refresh-token").cookie(new Cookie("oh_refresh", "refresh-old")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.responseObject.accessToken").isNotEmpty())
                .andExpect(header().string(HttpHeaders.SET_COOKIE, containsString("oh_refresh=refresh-new")));
    }

    @Test
    void refreshWithoutAValidCookieIsRejected() throws Exception {
        when(refreshTokenService.consume(isNull())).thenThrow(ErrorCode.UNAUTHORIZED.raise());

        mockMvc.perform(post(AUTH + "/refresh-token"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.errorCode").value("UNAUTHORIZED"));
    }

    @Test
    void refreshForALockedAccountEndsTheSession() throws Exception {
        User owner = givenUser("o1", "owner@onehostel.com", null, Role.HOSTEL_OWNER);
        owner.setLocked(true);
        when(userRepository.findById("o1")).thenReturn(Optional.of(owner));
        when(refreshTokenService.consume("refresh-old")).thenReturn(new RefreshTokenService.Rotation("o1", "fam-1"));

        mockMvc.perform(post(AUTH + "/refresh-token").cookie(new Cookie("oh_refresh", "refresh-old")))
                .andExpect(status().isUnauthorized());
        verify(refreshTokenService).revokeFamily("fam-1");
    }

    @Test
    void logoutRevokesTheSessionAndClearsTheCookie() throws Exception {
        mockMvc.perform(post(AUTH + "/logout").cookie(new Cookie("oh_refresh", "refresh-1")))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.SET_COOKIE,
                        allOf(containsString("oh_refresh="), containsString("Max-Age=0"))));
        verify(refreshTokenService).revokeSessionOf("refresh-1");
    }

    @Test
    void meReturnsTheProfileWithRoomAndBed() throws Exception {
        User tenant = givenUser("t1", "tenant@onehostel.com", null, Role.HOSTEL_TENANT);
        tenant.setRoomNumber("101");
        tenant.setBedId("ROOM-101-BED-A");
        String token = loginAndGetToken(login("tenant@onehostel.com", PASSWORD, "PASSWORD", "HOSTEL_TENANT"));
        when(userRepository.findById("t1")).thenReturn(Optional.of(tenant));

        mockMvc.perform(get(AUTH + "/me").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.responseObject.roomNumber").value("101"))
                .andExpect(jsonPath("$.responseObject.bedId").value("ROOM-101-BED-A"))
                .andExpect(jsonPath("$.responseObject.permissions").value(hasItem("HOSTEL_READ")));
    }

    @Test
    void tamperedTokenIsRejected() throws Exception {
        givenUser("t1", "tenant@onehostel.com", null, Role.HOSTEL_TENANT);
        String token = loginAndGetToken(login("tenant@onehostel.com", PASSWORD, "PASSWORD", "HOSTEL_TENANT"));
        String tampered = token.substring(0, token.length() - 4) + "AAAA";

        mockMvc.perform(get(AUTH + "/me").header("Authorization", "Bearer " + tampered))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.errorCode").value("UNAUTHORIZED"));
    }

    @Test
    void roleAreasOnlyAdmitTheirOwnRole() throws Exception {
        givenUser("a", "admin@onehostel.com", null, Role.SUPER_ADMIN);
        givenUser("o", "owner@onehostel.com", null, Role.HOSTEL_OWNER);
        givenUser("t", "tenant@onehostel.com", null, Role.HOSTEL_TENANT);
        String admin = loginAndGetToken(login("admin@onehostel.com", PASSWORD, "PASSWORD", "SUPER_ADMIN"));
        String owner = loginAndGetToken(login("owner@onehostel.com", PASSWORD, "PASSWORD", "HOSTEL_OWNER"));
        String tenant = loginAndGetToken(login("tenant@onehostel.com", PASSWORD, "PASSWORD", "HOSTEL_TENANT"));

        // 404 = got past security (no endpoint there yet); 403 = role refused.
        expectStatus("/api/v1/admin/anything", admin, 404);
        expectStatus("/api/v1/admin/anything", owner, 403);
        expectStatus("/api/v1/admin/anything", tenant, 403);

        expectStatus("/api/v1/owner/anything", owner, 404);
        expectStatus("/api/v1/owner/anything", admin, 403);
        expectStatus("/api/v1/owner/anything", tenant, 403);

        expectStatus("/api/v1/tenant/anything", tenant, 404);
        expectStatus("/api/v1/tenant/anything", admin, 403);
        expectStatus("/api/v1/tenant/anything", owner, 403);
    }

    private void expectLoginError(int status, String errorCode) throws Exception {
        mockMvc.perform(login("tenant@onehostel.com", PASSWORD, "PASSWORD", "HOSTEL_TENANT"))
                .andExpect(status().is(status))
                .andExpect(jsonPath("$.errorCode").value(errorCode));
    }

    private User givenUser(String id, String email, String phone, Role role) {
        User user = new User("Test " + role, email, passwordEncoder.encode(PASSWORD), role);
        user.setId(id);
        user.setPhoneNumber(phone);
        when(userRepository.findByEmail(email)).thenReturn(Optional.of(user));
        if (phone != null) {
            when(userRepository.findByPhoneNumber(eq(phone))).thenReturn(Optional.of(user));
        }
        return user;
    }

    private RequestBuilder login(String identifier, String secret, String authMode, String userType) throws Exception {
        return json(AUTH + "/login",
                Map.of("identifier", identifier, "secret", secret, "authMode", authMode, "userType", userType));
    }

    private RequestBuilder json(String path, Map<String, String> payload) throws Exception {
        String body = objectMapper.writeValueAsString(Map.of("requestObject", payload, "clientSource", "WEB"));
        return post(path).contentType(MediaType.APPLICATION_JSON).content(body);
    }

    private String loginAndGetToken(RequestBuilder login) throws Exception {
        String json = mockMvc.perform(login)
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(json).path("responseObject").path("accessToken").asText();
    }

    private void expectStatus(String path, String token, int expected) throws Exception {
        mockMvc.perform(get(path).header("Authorization", "Bearer " + token))
                .andExpect(status().is(expected));
    }
}
