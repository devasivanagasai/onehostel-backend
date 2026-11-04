package com.onehostel.access.vo;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

/** @param number 10 digits starting with 6-9, stored without spaces */
public record MobileNumber(
        @NotBlank String countryCode,
        @NotBlank @Pattern(regexp = "^[6-9]\\d{9}$", message = "Mobile number must be 10 digits starting with 6-9") String number) {

    /** "+919848012345", the form an SMS provider takes. */
    public String international() {
        return (countryCode != null ? countryCode : "") + number;
    }
}
