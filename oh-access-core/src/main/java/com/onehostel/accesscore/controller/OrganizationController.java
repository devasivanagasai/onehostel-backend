package com.onehostel.accesscore.controller;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.onehostel.accesscore.dto.organization.OrganizationRequest;
import com.onehostel.accesscore.dto.organization.OrganizationView;
import com.onehostel.accesscore.service.OrganizationService;
import com.onehostel.common.constants.ServiceConstants;
import com.onehostel.common.dto.CommonRequestObject;
import com.onehostel.common.dto.CommonResponseObject;
import com.onehostel.common.dto.IdRequest;
import com.onehostel.common.dto.ListFilter;
import com.onehostel.common.dto.PageResponse;
import com.onehostel.common.security.AuthenticatedUser;

import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

/** The same five actions as every module; create, update and delete are super-admin only. */
@Tag(name = "Organizations")
@RestController
@RequestMapping(ServiceConstants.ORGANIZATION_BASE)
@RequiredArgsConstructor
public class OrganizationController {

    private final OrganizationService service;

    @PostMapping(ServiceConstants.ACTION_CREATE)
    public ResponseEntity<CommonResponseObject<OrganizationView>> create(
            @Valid @RequestBody CommonRequestObject<OrganizationRequest> request,
            @AuthenticationPrincipal AuthenticatedUser user) {
        return ResponseEntity.status(HttpStatus.CREATED).body(CommonResponseObject.of(HttpStatus.CREATED.value(),
                "Organization created successfully.", service.create(request.requestObject(), user)));
    }

    @PostMapping(ServiceConstants.ACTION_UPDATE)
    public CommonResponseObject<OrganizationView> update(@Valid @RequestBody CommonRequestObject<OrganizationRequest> request,
            @AuthenticationPrincipal AuthenticatedUser user) {
        return CommonResponseObject.of(HttpStatus.OK.value(), "Organization updated successfully.",
                service.update(request.requestObject(), user));
    }

    @PostMapping(ServiceConstants.ACTION_INQUIRE)
    public CommonResponseObject<OrganizationView> inquire(@Valid @RequestBody CommonRequestObject<IdRequest> request,
            @AuthenticationPrincipal AuthenticatedUser user) {
        return CommonResponseObject.of(HttpStatus.OK.value(), "OK", service.inquire(request.requestObject().id(), user));
    }

    @PostMapping(ServiceConstants.ACTION_GET_ALL)
    public CommonResponseObject<PageResponse<OrganizationView>> getAll(
            @Valid @RequestBody CommonRequestObject<ListFilter> request, @AuthenticationPrincipal AuthenticatedUser user) {
        return CommonResponseObject.of(HttpStatus.OK.value(), "OK", service.getAll(request, user));
    }

    @PostMapping(ServiceConstants.ACTION_DELETE)
    public CommonResponseObject<Void> delete(@Valid @RequestBody CommonRequestObject<IdRequest> request,
            @AuthenticationPrincipal AuthenticatedUser user) {
        service.delete(request.requestObject().id(), user);
        return CommonResponseObject.of(HttpStatus.OK.value(), "Organization deleted successfully.", null);
    }
}
