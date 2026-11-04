package com.onehostel.access.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.onehostel.access.dto.PrivilegeDtos.PrivilegeMatrixResponse;
import com.onehostel.access.service.PrivilegeService;

import lombok.RequiredArgsConstructor;

/**
 * Service-to-service only: the gateway has no route to /internal/**, and InternalSecretFilter refuses
 * callers without X-Internal-Secret.
 */
@RestController
@RequestMapping("/internal/v1/privileges")
@RequiredArgsConstructor
public class InternalPrivilegeController {

    private final PrivilegeService privilegeService;

    /** Read by the gateway to authorize every API call. */
    @GetMapping("/matrix")
    public PrivilegeMatrixResponse matrix() {
        return privilegeService.matrix();
    }
}
