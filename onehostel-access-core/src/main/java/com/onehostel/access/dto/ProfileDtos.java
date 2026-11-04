package com.onehostel.access.dto;

import java.time.Instant;

import com.onehostel.access.constants.AccessEnums.Role;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** Request and response records of /api/v1/access/me (the signed-in user's own profile). */
public final class ProfileDtos {

    private ProfileDtos() {
    }

    /** @param memberSince when the account was created */
    public record ProfileResponse(String userId, String username, String firstName, String lastName, String email,
            String mobileCountryCode, String mobileNumber, Role role, Instant memberSince, Instant passwordChangedAt) {
    }

    public record ProfileUpdateRequest(
            @NotBlank(message = "firstName is required") @Size(max = 50) String firstName,
            @NotBlank(message = "lastName is required") @Size(max = 50) String lastName,
            @NotBlank(message = "email is required") @Email(message = "email is not valid") @Size(max = 254) String email,
            @NotBlank(message = "mobileCountryCode is required") @Pattern(regexp = "^\\+\\d{1,4}$", message = "mobileCountryCode must look like +91") String mobileCountryCode,
            @NotBlank(message = "mobileNumber is required") @Pattern(regexp = "^[6-9]\\d{9}$", message = "Mobile number must be 10 digits starting with 6-9") String mobileNumber) {
    }
}
