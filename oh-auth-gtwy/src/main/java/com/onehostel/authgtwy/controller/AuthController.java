package com.onehostel.controller;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.onehostel.constants.ServiceConstants;
import com.onehostel.dto.auth.LoginRequest;
import com.onehostel.dto.auth.LoginResponse;
import com.onehostel.dto.auth.OtpSendRequest;
import com.onehostel.dto.auth.OtpSendResponse;
import com.onehostel.dto.auth.UserProfile;
import com.onehostel.dto.helper.CommonRequestObject;
import com.onehostel.dto.helper.CommonResponseObject;
import com.onehostel.security.AuthenticatedUser;
import com.onehostel.security.RefreshCookie;
import com.onehostel.service.AuthService;
import com.onehostel.service.RefreshTokenService;
import com.onehostel.service.otp.OtpService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@Tag(name = "Authentication")
@RestController
@RequestMapping(ServiceConstants.AUTH_BASE)
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;
    private final OtpService otpService;
    private final RefreshTokenService refreshTokenService;

    @Operation(summary = "Log in as a super admin, hostel owner or hostel tenant",
            description = "authMode PASSWORD for everyone, PASSCODE for super admins, OTP for owners and tenants "
                    + "(secret = the code from /otp/send).")
    @SecurityRequirements
    @PostMapping(ServiceConstants.AUTH_LOGIN)
    public ResponseEntity<CommonResponseObject<LoginResponse>> login(
            @Valid @RequestBody CommonRequestObject<LoginRequest> request) {
        return withSession(authService.login(request.requestObject()), "Authentication successful.");
    }

    @Operation(summary = "OTP login step 1: send a code to an owner's or tenant's mobile or email")
    @SecurityRequirements
    @PostMapping(ServiceConstants.AUTH_OTP_SEND)
    public CommonResponseObject<OtpSendResponse> sendOtp(@Valid @RequestBody CommonRequestObject<OtpSendRequest> request) {
        return CommonResponseObject.of(HttpStatus.OK.value(),
                "If an account matches, a code has been sent.", otpService.send(request.requestObject()));
    }

    @Operation(summary = "New access token from the refresh-token cookie (rotates the cookie)")
    @SecurityRequirements
    @PostMapping(ServiceConstants.AUTH_REFRESH)
    public ResponseEntity<CommonResponseObject<LoginResponse>> refresh(
            @CookieValue(name = ServiceConstants.REFRESH_COOKIE_NAME, required = false) String refreshToken) {
        return withSession(authService.refresh(refreshToken), "Token refreshed.");
    }

    @Operation(summary = "End the session: revokes the refresh token and clears its cookie")
    @SecurityRequirements
    @PostMapping(ServiceConstants.AUTH_LOGOUT)
    public ResponseEntity<CommonResponseObject<Void>> logout(
            @CookieValue(name = ServiceConstants.REFRESH_COOKIE_NAME, required = false) String refreshToken) {
        authService.logout(refreshToken);
        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, RefreshCookie.cleared().toString())
                .body(CommonResponseObject.of(HttpStatus.OK.value(), "Logged out.", null));
    }

    @Operation(summary = "The signed-in user")
    @GetMapping(ServiceConstants.AUTH_ME)
    public CommonResponseObject<UserProfile> me(@AuthenticationPrincipal AuthenticatedUser user) {
        return CommonResponseObject.of(HttpStatus.OK.value(), "OK", authService.currentUser(user));
    }

    private ResponseEntity<CommonResponseObject<LoginResponse>> withSession(AuthService.AuthResult result,
            String message) {
        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE,
                        RefreshCookie.of(result.refreshToken(), refreshTokenService.ttl()).toString())
                .body(CommonResponseObject.of(HttpStatus.OK.value(), message, result.response()));
    }
}
