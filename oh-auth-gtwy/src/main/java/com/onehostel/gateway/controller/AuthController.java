package com.onehostel.gateway.controller;

import com.onehostel.gateway.config.DevOtpPolicy;
import com.onehostel.gateway.service.AuthService;
import com.onehostel.gateway.service.OtpService;
import com.onehostel.gateway.service.PasswordChangeService;
import com.onehostel.gateway.service.PasswordResetService;
import org.springframework.http.ResponseEntity;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.onehostel.core.dto.CommonRequestObject;
import com.onehostel.core.dto.CommonResponseObject;
import com.onehostel.core.security.KeycloakTokenResponse;
import com.onehostel.core.vo.request.ChangePasswordRequestVo;
import com.onehostel.core.vo.request.ForgotPasswordResetRequestVo;
import com.onehostel.core.vo.request.LoginRequestVo;
import com.onehostel.core.vo.request.OtpSendRequestVo;
import com.onehostel.core.vo.request.OtpVerifyRequestVo;
import com.onehostel.core.vo.response.OtpSendResponseVo;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import reactor.core.publisher.Mono;

/**
 * Public authentication endpoints. Accounts are provisioned by superiors
 * (SuperAdmin -> Owner -> Manager -> Security/Tenant); there is no self-registration.
 */
@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;
    private final OtpService otpService;
    private final PasswordResetService passwordResetService;
    private final PasswordChangeService passwordChangeService;
    private final DevOtpPolicy devOtpPolicy;

    @PostMapping("/login")
    public Mono<ResponseEntity<CommonResponseObject<KeycloakTokenResponse>>> login(
            @Valid @RequestBody CommonRequestObject<LoginRequestVo> request,
            @RequestHeader(value = "X-Device-Id", required = false) String deviceId,
            ServerHttpRequest httpRequest) {

        return authService.login(request.getRequestObject(), request.getClientSource(), deviceId,
                        extractClientIp(httpRequest), httpRequest.getHeaders().getFirst("User-Agent"))
                .map(ResponseEntity::ok);
    }

    @PostMapping("/otp/send")
    public Mono<ResponseEntity<CommonResponseObject<OtpSendResponseVo>>> sendOtp(
            @Valid @RequestBody CommonRequestObject<OtpSendRequestVo> request,
            ServerHttpRequest httpRequest) {

        return otpService.send(request.getRequestObject(), extractClientIp(httpRequest))
                .map(ResponseEntity::ok);
    }

    @PostMapping("/otp/verify")
    public Mono<ResponseEntity<CommonResponseObject<KeycloakTokenResponse>>> verifyOtp(
            @Valid @RequestBody CommonRequestObject<OtpVerifyRequestVo> request,
            @RequestHeader(value = "X-Device-Id", required = false) String deviceId,
            ServerHttpRequest httpRequest) {

        return otpService.verifyForLogin(request.getRequestObject(), devOtpPolicy.acceptsAnyCode(httpRequest),
                        request.getClientSource(), deviceId,
                        extractClientIp(httpRequest), httpRequest.getHeaders().getFirst("User-Agent"))
                .map(ResponseEntity::ok);
    }

    @PostMapping("/forgot-password/reset")
    public Mono<ResponseEntity<CommonResponseObject<Void>>> resetForgottenPassword(
            @Valid @RequestBody CommonRequestObject<ForgotPasswordResetRequestVo> request,
            ServerHttpRequest httpRequest) {

        return passwordResetService.reset(request.getRequestObject(), devOtpPolicy.acceptsAnyCode(httpRequest))
                .map(ResponseEntity::ok);
    }

    /** Requires a valid access token (see SecurityConfig); the current session stays signed in. */
    @PostMapping("/change-password")
    public Mono<ResponseEntity<CommonResponseObject<Void>>> changePassword(
            @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody CommonRequestObject<ChangePasswordRequestVo> request) {

        return passwordChangeService.change(jwt, request.getRequestObject())
                .map(ResponseEntity::ok);
    }

    @PostMapping("/refresh-token")
    public Mono<ResponseEntity<CommonResponseObject<KeycloakTokenResponse>>> refreshToken(
            @RequestHeader("X-Refresh-Token") String refreshToken,
            @RequestHeader(value = "X-Device-Id", required = false) String deviceId,
            ServerHttpRequest httpRequest) {

        return authService.refreshToken(refreshToken, deviceId,
                        extractClientIp(httpRequest), httpRequest.getHeaders().getFirst("User-Agent"))
                .map(ResponseEntity::ok);
    }

    @PostMapping("/logout")
    public Mono<ResponseEntity<CommonResponseObject<Void>>> logout(
            @RequestHeader(value = "X-Refresh-Token", required = false) String refreshToken) {

        return authService.logout(refreshToken)
                .map(ResponseEntity::ok);
    }

    private String extractClientIp(ServerHttpRequest request) {
        String forwardedFor = request.getHeaders().getFirst("X-Forwarded-For");
        if (forwardedFor != null && !forwardedFor.isBlank()) {
            return forwardedFor.split(",")[0].trim();
        }
        return request.getRemoteAddress() != null
                ? request.getRemoteAddress().getAddress().getHostAddress()
                : "UNKNOWN";
    }
}
