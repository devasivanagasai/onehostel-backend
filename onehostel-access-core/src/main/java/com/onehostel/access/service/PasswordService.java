package com.onehostel.access.service;

import org.springframework.stereotype.Service;

import com.onehostel.access.client.KeycloakAdminClient;
import com.onehostel.access.client.KeycloakAuthClient;
import com.onehostel.access.constants.AccessEnums.EntityType;
import com.onehostel.access.constants.AccessEnums.OtpPurpose;
import com.onehostel.access.dto.AuthRequests.ChangePasswordRequest;
import com.onehostel.access.dto.AuthRequests.ForgotPasswordResetRequest;
import com.onehostel.access.exception.ErrorCode;
import com.onehostel.access.helper.DevOtpPolicy;
import com.onehostel.access.model.OtpChallenge;
import com.onehostel.access.model.User;
import com.onehostel.access.vo.ClientInfo;
import com.onehostel.common.constants.CommonEnums.AuditAction;
import com.onehostel.common.exception.CommonErrorCode;
import com.onehostel.common.service.AuditRecorder;
import com.onehostel.common.vo.CurrentUser;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/** Change password (signed in) and forgot-password reset (with a FORGOT_PASSWORD code). */
@Slf4j
@Service
@RequiredArgsConstructor
public class PasswordService {

    private final UserLookupService userLookup;
    private final KeycloakAuthClient keycloak;
    private final KeycloakAdminClient keycloakAdmin;
    private final RefreshTokenService refreshTokens;
    private final OtpService otpService;
    private final DevOtpPolicy devOtpPolicy;
    private final AuditRecorder audit;

    /** The caller's current session keeps working; every other session (Keycloak and stored refresh tokens) ends. */
    public void change(CurrentUser caller, ChangePasswordRequest request) {
        User user = userLookup.findByKeycloakIdOrUsername(caller.subject(), caller.username())
                .orElseThrow(() -> CommonErrorCode.ENTITY_NOT_FOUND.raiseWithMessage("Account not found"));
        userLookup.ensureCanSignIn(user);
        if (!keycloak.isPasswordCorrect(user.keycloakUsername(), request.currentPassword())) {
            throw ErrorCode.INVALID_CURRENT_PASSWORD.raise();
        }
        if (request.newPassword().equals(request.currentPassword())) {
            throw ErrorCode.PASSWORD_REUSED.raise();
        }
        String keycloakUserId = keycloakAdmin.requireUserId(user.getKeycloakUserId(), user.keycloakUsername());
        keycloakAdmin.resetPassword(keycloakUserId, request.newPassword());

        // Only once the password really changed: end every other session
        userLookup.markPasswordChanged(user.getId());
        int revoked = refreshTokens.revokeAllExceptSession(user.getId(), caller.sessionId());
        int ended = keycloakAdmin.logoutOtherSessions(keycloakUserId, caller.sessionId());
        log.info("Password changed for user {}: {} refresh token(s) revoked, {} Keycloak session(s) ended", user.getId(), revoked, ended);
        audit.record(AuditAction.PASSWORD_CHANGE, EntityType.USER.name(), user.getId(), user.getOrganizationId(), "Changed by the user");
    }

    /** Sets a new password with a FORGOT_PASSWORD code; every session of the user ends. */
    public void reset(ForgotPasswordResetRequest request, ClientInfo client) {
        String identifier = UserLookupService.normalize(request.identifier());
        OtpChallenge challenge = otpService.check(identifier, OtpPurpose.FORGOT_PASSWORD, request.otp(), devOtpPolicy.acceptsAnyCode(client));
        User user = userLookup.findByIdentifier(identifier).orElseThrow(ErrorCode.INVALID_OTP::raise);
        userLookup.ensureCanSignIn(user);
        String keycloakUserId = keycloakAdmin.requireUserId(user.getKeycloakUserId(), user.keycloakUsername());
        keycloakAdmin.resetPassword(keycloakUserId, request.newPassword());

        // Burn the code only once the password has actually changed
        otpService.consume(challenge);
        userLookup.markPasswordChanged(user.getId());
        int revoked = refreshTokens.revokeAllExceptSession(user.getId(), null);
        keycloakAdmin.logoutAllSessions(keycloakUserId);
        log.info("Password reset for user {}: {} refresh token(s) revoked", user.getId(), revoked);
        audit.record(AuditAction.PASSWORD_CHANGE, EntityType.USER.name(), user.getId(), user.getOrganizationId(), "Reset with a one-time code");
    }
}
