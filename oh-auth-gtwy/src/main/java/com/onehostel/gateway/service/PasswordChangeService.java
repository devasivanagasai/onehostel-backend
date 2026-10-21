package com.onehostel.gateway.service;

import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;

import com.onehostel.core.dto.CommonResponseObject;
import com.onehostel.core.exception.ErrorCode;
import com.onehostel.core.vo.request.ChangePasswordRequestVo;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Mono;

/**
 * Change password for a signed-in user. The caller's current session keeps working;
 * every other session (Keycloak and stored refresh tokens) is ended.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PasswordChangeService {

    private final UserLookupService userLookupService;
    private final KeycloakAuthService keycloakAuthService;
    private final KeycloakAdminService keycloakAdminService;
    private final AuthService authService;

    public Mono<CommonResponseObject<Void>> change(Jwt jwt, ChangePasswordRequestVo request) {
        String keycloakUserId = jwt.getSubject();
        String currentSessionId = jwt.getClaimAsString("sid");

        return userLookupService.findByKeycloakIdOrUsername(keycloakUserId, jwt.getClaimAsString("preferred_username"))
                .switchIfEmpty(Mono.error(ErrorCode.ENTITY_NOT_FOUND.raiseWithMessage("Account not found")))
                .flatMap(userLookupService::ensureCanSignIn)
                .flatMap(user -> keycloakAuthService
                        .isPasswordCorrect(UserLookupService.keycloakUsername(user), request.getCurrentPassword())
                        .flatMap(correct -> {
                            if (!correct) {
                                return Mono.error(ErrorCode.INVALID_CURRENT_PASSWORD.raise());
                            }
                            if (request.getNewPassword().equals(request.getCurrentPassword())) {
                                return Mono.error(ErrorCode.PASSWORD_REUSED.raise());
                            }
                            return keycloakAdminService.resetPassword(keycloakUserId, request.getNewPassword())
                                    .then(Mono.defer(() -> userLookupService.markPasswordChanged(user.getId())))
                                    // Only after the password really changed: end every other session
                                    .then(Mono.defer(() -> Mono.zip(
                                            authService.revokeRefreshTokensExceptSession(user.getId(), currentSessionId),
                                            keycloakAdminService.logoutOtherSessions(keycloakUserId, currentSessionId))))
                                    .doOnNext(ended -> log.info(
                                            "AUDIT password changed: user={} keycloakId={} revokedRefreshTokens={} endedSessions={}",
                                            user.getId(), keycloakUserId, ended.getT1(), ended.getT2()))
                                    .then();
                        }))
                .thenReturn(CommonResponseObject.<Void>success("Password changed successfully.", null, null));
    }
}
