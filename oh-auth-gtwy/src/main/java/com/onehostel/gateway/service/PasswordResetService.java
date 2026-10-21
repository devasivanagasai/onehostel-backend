package com.onehostel.gateway.service;

import org.springframework.stereotype.Service;

import com.onehostel.core.constants.EnumHelper.OtpPurpose;
import com.onehostel.core.dto.CommonResponseObject;
import com.onehostel.core.exception.ErrorCode;
import com.onehostel.core.vo.request.ForgotPasswordResetRequestVo;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Mono;

/**
 * Forgot-password flow: the user requests a FORGOT_PASSWORD code via /otp/send, then submits it
 * here with the new password. On success every existing session of the user is ended.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PasswordResetService {

    private final OtpService otpService;
    private final UserLookupService userLookupService;
    private final KeycloakAdminService keycloakAdminService;
    private final AuthService authService;

    public Mono<CommonResponseObject<Void>> reset(ForgotPasswordResetRequestVo request, boolean acceptAnyCode) {
        String identifier = UserLookupService.normalize(request.getIdentifier());

        return otpService.checkChallenge(identifier, OtpPurpose.FORGOT_PASSWORD, request.getOtp(), acceptAnyCode)
                .flatMap(challenge -> userLookupService.findByIdentifier(identifier)
                        .switchIfEmpty(Mono.error(ErrorCode.INVALID_OTP.raise()))
                        .flatMap(userLookupService::ensureCanSignIn)
                        .flatMap(user -> otpService.requireKeycloakId(user)
                                .flatMap(keycloakId -> keycloakAdminService.resetPassword(keycloakId, request.getNewPassword())
                                        // Only burn the code once the password has actually changed
                                        .then(Mono.defer(() -> otpService.consume(challenge)))
                                        .then(Mono.defer(() -> userLookupService.markPasswordChanged(user.getId())))
                                        .then(Mono.defer(() -> authService.revokeAllRefreshTokens(user.getId())))
                                        .flatMap(revoked -> keycloakAdminService.logoutAllSessions(keycloakId)
                                                .doOnSuccess(v -> log.info("Password reset for user {}; {} refresh token(s) revoked",
                                                        user.getId(), revoked))))))
                .thenReturn(CommonResponseObject.<Void>success(
                        "Password reset successfully. Please log in with your new password.", null, null));
    }
}
