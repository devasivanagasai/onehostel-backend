package com.onehostel.access.dto;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.onehostel.access.constants.AccessEnums.OtpChannel;
import com.onehostel.access.constants.AccessEnums.OtpPurpose;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** Request and response bodies of /api/v1/auth (the requestObject of the envelope). */
public final class AuthRequests {

    /** 8-128 chars, an uppercase and a lowercase letter, a digit and a special character, no whitespace. */
    static final String PASSWORD_POLICY = "^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d)(?=.*[^A-Za-z0-9\\s])\\S{8,128}$";
    static final String PASSWORD_POLICY_MESSAGE =
            "Password must be 8-128 characters with an uppercase letter, a lowercase letter, a digit and a special character, and no spaces";
    static final String OTP_FORMAT = "^\\d{6}$";

    private AuthRequests() {
    }

    /** @param identifier email, mobile number or username ("userName" and "email" are accepted as older names) */
    public record LoginRequest(
            @NotBlank(message = "identifier is required") @Size(max = 254) @JsonAlias({"userName", "email"}) String identifier,
            @NotBlank(message = "password is required") @Size(max = 128) String password) {

        @Override
        public String toString() {
            return "LoginRequest[identifier=" + identifier + ", password=[PROTECTED]]";
        }
    }

    /** @param purpose LOGIN when omitted */
    public record OtpSendRequest(
            @NotBlank(message = "identifier is required") @Size(max = 254) String identifier,
            OtpPurpose purpose) {
    }

    public record OtpVerifyRequest(
            @NotBlank(message = "identifier is required") @Size(max = 254) String identifier,
            @NotBlank(message = "otp is required") @Pattern(regexp = OTP_FORMAT, message = "otp must be 6 digits") String otp) {

        @Override
        public String toString() {
            return "OtpVerifyRequest[identifier=" + identifier + ", otp=[PROTECTED]]";
        }
    }

    public record ForgotPasswordResetRequest(
            @NotBlank(message = "identifier is required") @Size(max = 254) String identifier,
            @NotBlank(message = "otp is required") @Pattern(regexp = OTP_FORMAT, message = "otp must be 6 digits") String otp,
            @NotBlank(message = "newPassword is required") @Pattern(regexp = PASSWORD_POLICY, message = PASSWORD_POLICY_MESSAGE) String newPassword) {

        @Override
        public String toString() {
            return "ForgotPasswordResetRequest[identifier=" + identifier + ", otp=[PROTECTED], newPassword=[PROTECTED]]";
        }
    }

    public record ChangePasswordRequest(
            @NotBlank(message = "currentPassword is required") @Size(max = 128) String currentPassword,
            @NotBlank(message = "newPassword is required") @Pattern(regexp = PASSWORD_POLICY, message = PASSWORD_POLICY_MESSAGE) String newPassword) {

        @Override
        public String toString() {
            return "ChangePasswordRequest[currentPassword=[PROTECTED], newPassword=[PROTECTED]]";
        }
    }

    /** @param devOtp the code itself, only in OTP dev mode (local profile) */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record OtpSendResponse(long expiresInSeconds, OtpChannel channel, String devOtp) {
    }
}
