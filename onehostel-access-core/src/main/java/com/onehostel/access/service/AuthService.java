package com.onehostel.access.service;

import org.springframework.stereotype.Service;

import com.onehostel.access.client.KeycloakAdminClient;
import com.onehostel.access.client.KeycloakAuthClient;
import com.onehostel.access.constants.AccessEnums.EntityType;
import com.onehostel.access.constants.AccessEnums.OtpPurpose;
import com.onehostel.access.dto.AuthRequests.LoginRequest;
import com.onehostel.access.dto.AuthRequests.OtpVerifyRequest;
import com.onehostel.access.dto.AuthTokenResponse;
import com.onehostel.access.dto.KeycloakTokenResponse;
import com.onehostel.access.exception.ErrorCode;
import com.onehostel.access.helper.DevOtpPolicy;
import com.onehostel.access.model.OtpChallenge;
import com.onehostel.access.model.RefreshToken;
import com.onehostel.access.model.User;
import com.onehostel.access.repository.UserRepository;
import com.onehostel.access.vo.ClientInfo;
import com.onehostel.common.constants.CommonEnums.AuditAction;
import com.onehostel.common.exception.HostelException;
import com.onehostel.common.service.AuditRecorder;

import lombok.RequiredArgsConstructor;

/**
 * Sign-in (password or OTP), refresh and logout. Keycloak holds the passwords and issues the tokens;
 * this service decides who may sign in and keeps the record of issued refresh tokens.
 * Account state is checked only after the credential is proven, so a wrong password never reveals it.
 */
@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserLookupService userLookup;
    private final UserRepository userRepository;
    private final KeycloakAuthClient keycloak;
    private final KeycloakAdminClient keycloakAdmin;
    private final RefreshTokenService refreshTokens;
    private final OtpService otpService;
    private final DevOtpPolicy devOtpPolicy;
    private final AuditRecorder audit;
    private final PrivilegeService privileges;

    /** Password login with an email, mobile number or username. */
    public AuthTokenResponse login(LoginRequest request, ClientInfo client) {
        User user = userLookup.findByIdentifier(UserLookupService.normalize(request.identifier()))
                .orElseThrow(ErrorCode.INVALID_CREDENTIALS::raise);
        KeycloakTokenResponse tokens = keycloak.loginWithPassword(user.keycloakUsername(), request.password());
        return openSession(user, tokens, client, "password");
    }

    /** OTP login: checks a LOGIN code, then has Keycloak issue tokens for the user. */
    public AuthTokenResponse loginWithOtp(OtpVerifyRequest request, ClientInfo client) {
        String identifier = UserLookupService.normalize(request.identifier());
        OtpChallenge challenge = otpService.check(identifier, OtpPurpose.LOGIN, request.otp(), devOtpPolicy.acceptsAnyCode(client));
        otpService.consume(challenge);
        User user = userLookup.findByIdentifier(identifier).orElseThrow(ErrorCode.INVALID_OTP::raise);
        KeycloakTokenResponse tokens = keycloak.exchangeForUser(
                keycloakAdmin.requireUserId(user.getKeycloakUserId(), user.keycloakUsername()));
        return openSession(user, tokens, client, "otp");
    }

    /** Swaps a refresh token for a new pair; the old one is spent at once. */
    public AuthTokenResponse refresh(String rawRefreshToken, ClientInfo client) {
        RefreshToken stored = refreshTokens.requireUsable(rawRefreshToken);
        KeycloakTokenResponse tokens = keycloak.refresh(rawRefreshToken);
        refreshTokens.markRotated(stored, tokens);
        User user = userRepository.findById(stored.getUserId()).filter(u -> !u.isDeleted())
                .orElseThrow(ErrorCode.INVALID_REFRESH_TOKEN::raise);
        // A user locked or disabled since signing in loses the session at the next refresh
        return openSession(user, tokens, client, null);
    }

    /** Always succeeds: an unknown or already-revoked token simply has nothing left to end. */
    public void logout(String rawRefreshToken) {
        if (rawRefreshToken == null || rawRefreshToken.isBlank()) {
            return;
        }
        refreshTokens.revoke(rawRefreshToken);
        keycloak.revoke(rawRefreshToken);
    }

    private AuthTokenResponse openSession(User user, KeycloakTokenResponse tokens, ClientInfo client, String method) {
        try {
            userLookup.ensureCanSignIn(user);
            userLookup.ensureAllowedOnClient(user, client.clientSource());
        } catch (HostelException e) {
            // Keycloak already opened a session for a user who may not have one: close it, then refuse
            keycloak.endSession(tokens.refreshToken());
            throw e;
        }
        refreshTokens.store(tokens, user, client);
        if (method != null) {
            audit.record(AuditAction.LOGIN, EntityType.USER.name(), user.getId(), user.getOrganizationId(), "Signed in with " + method);
        }
        return AuthTokenResponse.of(tokens, privileges.privTokenForAccessToken(tokens.accessToken()));
    }
}
