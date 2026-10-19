package com.onehostel.controller.helper;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

import com.onehostel.constants.ServiceConstants;
import com.onehostel.dto.helper.CommonRequestObject;
import com.onehostel.dto.helper.CommonResponseObject;
import com.onehostel.dto.helper.HostelAbstractDto;
import com.onehostel.dto.helper.IdRequest;
import com.onehostel.dto.helper.ListFilter;
import com.onehostel.dto.helper.PageResponse;
import com.onehostel.security.AuthenticatedUser;
import com.onehostel.service.helper.HostelAbstractService;

import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.Valid;

public abstract class HostelCoreController<D extends HostelAbstractDto, V> {

    private final HostelAbstractService<?, D, V> service;

    protected HostelCoreController(HostelAbstractService<?, D, V> service) {
        this.service = service;
    }

    @Operation(summary = "Create")
    @PostMapping(ServiceConstants.ACTION_CREATE)
    public ResponseEntity<CommonResponseObject<V>> create(@Valid @RequestBody CommonRequestObject<D> request,
            @AuthenticationPrincipal AuthenticatedUser user) {
        V created = service.create(request.requestObject(), user);
        return ResponseEntity.status(HttpStatus.CREATED).body(CommonResponseObject.of(HttpStatus.CREATED.value(),
                service.entityLabel() + " created successfully.", created));
    }

    @Operation(summary = "Update")
    @PostMapping(ServiceConstants.ACTION_UPDATE)
    public CommonResponseObject<V> update(@Valid @RequestBody CommonRequestObject<D> request,
            @AuthenticationPrincipal AuthenticatedUser user) {
        return CommonResponseObject.of(HttpStatus.OK.value(), service.entityLabel() + " updated successfully.",
                service.update(request.requestObject(), user));
    }

    @Operation(summary = "Get one by id")
    @PostMapping(ServiceConstants.ACTION_INQUIRE)
    public CommonResponseObject<V> inquire(@Valid @RequestBody CommonRequestObject<IdRequest> request,
            @AuthenticationPrincipal AuthenticatedUser user) {
        return CommonResponseObject.of(HttpStatus.OK.value(), "OK",
                service.inquire(request.requestObject().id(), user));
    }

    @Operation(summary = "List, filtered and paged")
    @PostMapping(ServiceConstants.ACTION_GET_ALL)
    public CommonResponseObject<PageResponse<V>> getAll(@Valid @RequestBody CommonRequestObject<ListFilter> request,
            @AuthenticationPrincipal AuthenticatedUser user) {
        return CommonResponseObject.of(HttpStatus.OK.value(), "OK", service.getAll(request, user));
    }

    @Operation(summary = "Delete by id")
    @PostMapping(ServiceConstants.ACTION_DELETE)
    public CommonResponseObject<Void> delete(@Valid @RequestBody CommonRequestObject<IdRequest> request,
            @AuthenticationPrincipal AuthenticatedUser user) {
        service.delete(request.requestObject().id(), user);
        return CommonResponseObject.of(HttpStatus.OK.value(), service.entityLabel() + " deleted successfully.", null);
    }
}
