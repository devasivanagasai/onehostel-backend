package com.onehostel.access.controller;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.onehostel.access.constants.AccessEnums.Role;
import com.onehostel.access.dto.PrivilegeDtos.PrivilegeResponse;
import com.onehostel.access.dto.PrivilegeDtos.RoleGrantsRequest;
import com.onehostel.access.dto.PrivilegeDtos.RoleGrantsResponse;
import com.onehostel.access.service.PrivilegeService;
import com.onehostel.common.annotation.ResponseMessage;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

/**
 * The privilege catalogue (any signed-in user: the Angular app needs it to read its privToken) and the role
 * editor (ROLE_MANAGE, enforced at the gateway).
 */
@RestController
@RequestMapping("/api/v1/access")
@RequiredArgsConstructor
public class PrivilegeController {

    private final PrivilegeService privilegeService;

    @GetMapping("/privileges")
    public List<PrivilegeResponse> privileges() {
        return privilegeService.catalog();
    }

    @GetMapping("/roles")
    public List<RoleGrantsResponse> roles() {
        return privilegeService.roles();
    }

    @PutMapping("/roles/{role}/privileges")
    @ResponseMessage("Role privileges updated")
    public RoleGrantsResponse updateRoleGrants(@PathVariable Role role, @Valid @RequestBody RoleGrantsRequest request) {
        return privilegeService.updateRoleGrants(role, request.privilegeCodes());
    }
}
