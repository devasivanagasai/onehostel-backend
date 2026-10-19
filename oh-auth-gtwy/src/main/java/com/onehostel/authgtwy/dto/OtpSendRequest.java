package com.onehostel.dto.auth;

import com.onehostel.constants.CommonEnumHelper.OtpChannel;
import com.onehostel.constants.CommonEnumHelper.Role;
import com.onehostel.constants.GlobalConstants;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Step 1 of OTP login (owners and tenants only). The code goes to the account's mobile (SMS) or
 * email, whichever {@code channel} names; step 2 is /login with authMode OTP.
 */
public record OtpSendRequest(
        @NotBlank @Size(max = GlobalConstants.EMAIL_MAX_LENGTH) String identifier,
        @NotNull OtpChannel channel,
        @NotNull Role userType) {
}
