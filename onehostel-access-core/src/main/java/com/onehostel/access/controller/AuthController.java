package com.onehostel.access.controller;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.onehostel.access.constants.AccessEnums.OtpPurpose;
import com.onehostel.access.dto.AuthRequests.ChangePasswordRequest;
import com.onehostel.access.dto.AuthRequests.ForgotPasswordResetRequest;
import com.onehostel.access.dto.AuthRequests.LoginRequest;
import com.onehostel.access.dto.AuthRequests.OtpSendRequest;
import com.onehostel.access.dto.AuthRequests.OtpSendResponse;
import com.onehostel.access.dto.AuthRequests.OtpVerifyRequest;
import com.onehostel.access.dto.AuthTokenResponse;
import com.onehostel.access.service.AuthService;
import com.onehostel.access.service.OtpService;
import com.onehostel.access.service.PasswordService;
import com.onehostel.access.vo.ClientInfo;
import com.onehostel.common.annotation.AuthenticatedUser;
import com.onehostel.common.annotation.ResponseMessage;
import com.onehostel.common.dto.CommonResponseObject;
import com.onehostel.common.vo.CurrentUser;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

/**
 * Authentication endpoints. Accounts are provisioned top-down (SuperAdmin -> Owner -> Manager ->
 * Security/Tenant); there is no self-registration. All but change-password are public at the gateway.
 */
@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class AuthController {

    private static final String DEVICE_ID = "X-Device-Id";
    private static final String REFRESH_TOKEN = "X-Refresh-Token";

    private final AuthService authService;
    private final PasswordService passwordService;
    private final OtpService otpService;

    @PostMapping("/login")
    @ResponseMessage("Login successful")
    public AuthTokenResponse login(@Valid @RequestBody LoginRequest request,
            @RequestHeader(value = DEVICE_ID, required = false) String deviceId, HttpServletRequest http) {
        return authService.login(request, ClientInfo.of(http, deviceId));
    }

    @PostMapping("/otp/send")
    @ResponseMessage("A verification code has been sent")
    public OtpSendResponse sendOtp(@Valid @RequestBody OtpSendRequest request, HttpServletRequest http) {
        OtpPurpose purpose = request.purpose() != null ? request.purpose() : OtpPurpose.LOGIN;
        OtpService.Issued issued = otpService.send(request.identifier(), purpose, ClientInfo.of(http, null));
        return new OtpSendResponse(issued.expiresInSeconds(), issued.channel(), issued.devCode());
    }

    @PostMapping("/otp/verify")
    @ResponseMessage("Login successful")
    public AuthTokenResponse verifyOtp(@Valid @RequestBody OtpVerifyRequest request,
            @RequestHeader(value = DEVICE_ID, required = false) String deviceId, HttpServletRequest http) {
        return authService.loginWithOtp(request, ClientInfo.of(http, deviceId));
    }

    @PostMapping("/forgot-password/reset")
    public CommonResponseObject<Void> resetForgottenPassword(@Valid @RequestBody ForgotPasswordResetRequest request,
            HttpServletRequest http) {
        passwordService.reset(request, ClientInfo.of(http, null));
        return CommonResponseObject.ok("Password reset successfully. Please log in with your new password.", null);
    }

    /** Needs an access token (enforced by the gateway); the current session stays signed in. */
    @PostMapping("/change-password")
    public CommonResponseObject<Void> changePassword(@AuthenticatedUser CurrentUser caller,
            @Valid @RequestBody ChangePasswordRequest request) {
        passwordService.change(caller, request);
        return CommonResponseObject.ok("Password changed successfully.", null);
    }

    @PostMapping("/refresh-token")
    @ResponseMessage("Token refreshed successfully")
    public AuthTokenResponse refreshToken(@RequestHeader(REFRESH_TOKEN) String refreshToken,
            @RequestHeader(value = DEVICE_ID, required = false) String deviceId, HttpServletRequest http) {
        return authService.refresh(refreshToken, ClientInfo.of(http, deviceId));
    }

    @PostMapping("/logout")
    public CommonResponseObject<Void> logout(@RequestHeader(value = REFRESH_TOKEN, required = false) String refreshToken) {
        authService.logout(refreshToken);
        return CommonResponseObject.ok("Logged out successfully", null);
    }
}
