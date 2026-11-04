package com.onehostel.access.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.onehostel.access.client.KeycloakAdminClient;
import com.onehostel.access.client.KeycloakAuthClient;
import com.onehostel.access.constants.AccessEnums.Role;
import com.onehostel.access.constants.AccessEnums.UserStatus;
import com.onehostel.access.dto.AuthRequests.LoginRequest;
import com.onehostel.access.dto.AuthTokenResponse;
import com.onehostel.access.dto.KeycloakTokenResponse;
import com.onehostel.access.exception.ErrorCode;
import com.onehostel.access.helper.DevOtpPolicy;
import com.onehostel.access.model.User;
import com.onehostel.access.repository.UserRepository;
import com.onehostel.access.vo.AccessScope;
import com.onehostel.access.vo.ClientInfo;
import com.onehostel.common.constants.CommonEnums.ClientSource;
import com.onehostel.common.exception.HostelException;
import com.onehostel.common.service.AuditRecorder;

class AuthServiceTest {

    private final UserRepository users = mock(UserRepository.class);
    private final KeycloakAuthClient keycloak = mock(KeycloakAuthClient.class);
    private final RefreshTokenService refreshTokens = mock(RefreshTokenService.class);
    private final PrivilegeService privileges = mock(PrivilegeService.class);
    private AuthService authService;

    private final KeycloakTokenResponse tokens = new KeycloakTokenResponse("access", 900L, "refresh", 1800L, "Bearer", "sid-1", null);
    private final ClientInfo web = new ClientInfo(ClientSource.WEB, null, "127.0.0.1", "test");

    @BeforeEach
    void setUp() {
        UserLookupService lookup = new UserLookupService(users, null);
        authService = new AuthService(lookup, users, keycloak, mock(KeycloakAdminClient.class), refreshTokens,
                mock(OtpService.class), mock(DevOtpPolicy.class), mock(AuditRecorder.class), privileges);
    }

    private User user(Role role, UserStatus status, boolean locked) {
        User user = new User();
        user.setId("01JUSER");
        user.setUsername("rajesh.kumar");
        user.setEmail("rajesh.kumar@onehostel.com");
        user.setStatus(status);
        user.setLocked(locked);
        user.setPrimaryAccess(new AccessScope("org-sunrise", null, role, null));
        when(users.findByEmailAndDeletedFalse("rajesh.kumar@onehostel.com")).thenReturn(Optional.of(user));
        return user;
    }

    @Test
    void activeOwnerSignsInAndTheRefreshTokenIsStored() {
        User owner = user(Role.HOSTEL_OWNER, UserStatus.ACTIVE, false);
        when(keycloak.loginWithPassword("rajesh.kumar", "Secret@123")).thenReturn(tokens);
        when(privileges.privTokenForAccessToken("access")).thenReturn("0110");

        AuthTokenResponse result = authService.login(new LoginRequest(" Rajesh.Kumar@OneHostel.com ", "Secret@123"), web);

        assertThat(result.accessToken()).isEqualTo("access");
        assertThat(result.refreshToken()).isEqualTo("refresh");
        assertThat(result.privToken()).isEqualTo("0110");
        verify(refreshTokens).store(tokens, owner, web);
    }

    @Test
    void lockedAccountIsRefusedOnlyAfterThePasswordAndItsSessionIsClosed() {
        user(Role.HOSTEL_OWNER, UserStatus.ACTIVE, true);
        when(keycloak.loginWithPassword("rajesh.kumar", "Secret@123")).thenReturn(tokens);

        assertThatThrownBy(() -> authService.login(new LoginRequest("rajesh.kumar@onehostel.com", "Secret@123"), web))
                .isInstanceOf(HostelException.class)
                .extracting(e -> ((HostelException) e).getErrorCode()).isEqualTo(ErrorCode.ACCOUNT_LOCKED);
        verify(keycloak).endSession("refresh");
        verify(refreshTokens, never()).store(any(), any(), any());
    }

    @Test
    void wrongPasswordSaysNothingAboutTheAccount() {
        user(Role.HOSTEL_OWNER, UserStatus.BLOCKED, true);
        when(keycloak.loginWithPassword("rajesh.kumar", "wrong")).thenThrow(ErrorCode.INVALID_CREDENTIALS.raise());

        assertThatThrownBy(() -> authService.login(new LoginRequest("rajesh.kumar@onehostel.com", "wrong"), web))
                .extracting(e -> ((HostelException) e).getErrorCode()).isEqualTo(ErrorCode.INVALID_CREDENTIALS);
    }

    @Test
    void unknownIdentifierIsJustInvalidCredentials() {
        assertThatThrownBy(() -> authService.login(new LoginRequest("nobody@onehostel.com", "x"), web))
                .extracting(e -> ((HostelException) e).getErrorCode()).isEqualTo(ErrorCode.INVALID_CREDENTIALS);
    }

    @Test
    void tenantsMustUseTheMobileApp() {
        user(Role.HOSTEL_TENANT, UserStatus.ACTIVE, false);
        when(keycloak.loginWithPassword("rajesh.kumar", "Secret@123")).thenReturn(tokens);

        assertThatThrownBy(() -> authService.login(new LoginRequest("rajesh.kumar@onehostel.com", "Secret@123"), web))
                .extracting(e -> ((HostelException) e).getErrorCode()).isEqualTo(ErrorCode.MOBILE_APP_ONLY);
        verify(keycloak).endSession("refresh");
    }

    @Test
    void identifiersAreNormalized() {
        assertThat(UserLookupService.normalize("+91 98480-12345")).isEqualTo("9848012345");
        assertThat(UserLookupService.normalize("09848012345")).isEqualTo("9848012345");
        assertThat(UserLookupService.normalize(" Super.Admin ")).isEqualTo("super.admin");
        assertThat(UserLookupService.normalize("Priya@OneHostel.com")).isEqualTo("priya@onehostel.com");
    }
}
