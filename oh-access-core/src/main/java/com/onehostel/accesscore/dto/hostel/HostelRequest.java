package com.onehostel.dto.hostel;

import com.onehostel.constants.CommonEnumHelper.Status;
import com.onehostel.constants.GlobalConstants;
import com.onehostel.dto.helper.HostelAbstractDto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** Create / update payload for a hostel. {@code status} defaults to ACTIVE on create. */
public record HostelRequest(
        @Size(max = 64) String id,
        Long version,
        @Size(max = 64) String organizationId,
        @NotBlank @Size(max = 120) String name,
        @NotBlank @Size(max = 250) String addressLine,
        @NotBlank @Size(max = 80) String city,
        @NotBlank @Size(max = 80) String state,
        @NotBlank @Pattern(regexp = GlobalConstants.PINCODE_REGEX) String pincode,
        @Pattern(regexp = GlobalConstants.PHONE_REGEX) String contactPhone,
        Status status) implements HostelAbstractDto {
}
