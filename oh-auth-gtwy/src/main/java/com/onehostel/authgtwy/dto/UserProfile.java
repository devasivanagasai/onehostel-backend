package com.onehostel.authgtwy.dto;

import java.util.List;

import com.onehostel.authgtwy.model.User;
import com.onehostel.common.constants.CommonEnumHelper.Role;
import com.onehostel.common.constants.CommonEnumHelper.UserStatus;

public record UserProfile(
        String userId,
        String fullName,
        String email,
        String mobileNumber,
        Role role,
        UserStatus status,
        String organizationId,
        String hostelId,
        String roomNumber,
        String bedId,
        List<String> permissions) {

    public static UserProfile from(User user) {
        return new UserProfile(user.getId(), user.getName(), user.getEmail(), user.getPhoneNumber(), user.getRole(),
                user.getStatus(), user.getOrganizationId(), user.getHostelId(), user.getRoomNumber(), user.getBedId(),
                user.getRole().permissions().stream().map(Enum::name).sorted().toList());
    }
}
