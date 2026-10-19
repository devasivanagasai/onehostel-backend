package com.onehostel.dto.auth;

import com.onehostel.constants.CommonEnumHelper.AuthMode;
import com.onehostel.constants.CommonEnumHelper.Role;
import com.onehostel.constants.GlobalConstants;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * One login for every account type.
 *
 * <ul>
 * <li>SUPER_ADMIN: {@code identifier} = email or username; {@code authMode} PASSWORD or PASSCODE.</li>
 * <li>HOSTEL_OWNER / HOSTEL_TENANT: {@code identifier} = email or 10-digit mobile; {@code authMode}
 * PASSWORD, or OTP with the code from /otp/send as {@code secret}.</li>
 * </ul>
 */
public record LoginRequest(
        @NotBlank @Size(max = GlobalConstants.EMAIL_MAX_LENGTH) String identifier,
        @NotBlank @Size(max = GlobalConstants.PASSWORD_MAX_LENGTH) String secret,
        @NotNull AuthMode authMode,
        @NotNull Role userType) {

    @Override
    public String toString() {
        return "LoginRequest[identifier=" + identifier + ", secret=***, authMode=" + authMode
                + ", userType=" + userType + "]";
    }
}
