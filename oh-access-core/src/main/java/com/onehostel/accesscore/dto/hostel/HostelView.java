package com.onehostel.dto.hostel;

import java.time.Instant;

import com.onehostel.constants.CommonEnumHelper.Status;
import com.onehostel.model.Hostel;

public record HostelView(
        String id,
        String organizationId,
        String name,
        String addressLine,
        String city,
        String state,
        String pincode,
        String contactPhone,
        Status status,
        Long version,
        Instant createdDateTime,
        Instant updatedDateTime) {

    public static HostelView from(Hostel hostel) {
        return new HostelView(hostel.getId(), hostel.getOrganizationId(), hostel.getName(), hostel.getAddressLine(),
                hostel.getCity(), hostel.getState(), hostel.getPincode(), hostel.getContactPhone(), hostel.getStatus(),
                hostel.getVersion(), hostel.getCreatedDateTime(), hostel.getUpdatedDateTime());
    }
}
