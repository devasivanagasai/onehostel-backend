package com.onehostel.core.vo.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.onehostel.core.constants.EnumHelper.Role;
import com.onehostel.core.constants.EnumHelper.UserStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.ALWAYS)
public class UserProfileVo {

    private String userId;
    private String fullName;
    private String email;
    private String mobileNumber;
    private Role role;
    private UserStatus status;
    private String organizationId;
    private String hostelId;
    private String roomNumber;
    private String bedId;
    private List<String> permissions;
}