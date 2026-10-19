package com.onehostel.authgtwy.gateway;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.onehostel.common.constants.ServiceConstants;
import com.onehostel.common.dto.CommonResponseObject;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;

/**
 * Gateway administration (super admin only, via EndpointPrivilege GATEWAY_MANAGE). After editing
 * the public-endpoint collection in the database, call refresh to apply it without a restart.
 */
@Tag(name = "Gateway")
@RestController
@RequestMapping(ServiceConstants.GATEWAY_BASE + "/public-endpoints")
@RequiredArgsConstructor
public class PublicEndpointController {

    private final PublicEndpointRegistry registry;

    @Operation(summary = "The active permit-all list (method and path pattern)")
    @GetMapping
    public CommonResponseObject<List<String>> list() {
        return CommonResponseObject.of(HttpStatus.OK.value(), "OK", registry.describe());
    }

    @Operation(summary = "Reload the permit-all list from the database")
    @PostMapping("/refresh")
    public CommonResponseObject<List<String>> refresh() {
        registry.refresh();
        return CommonResponseObject.of(HttpStatus.OK.value(), "Public endpoint list reloaded.", registry.describe());
    }
}
