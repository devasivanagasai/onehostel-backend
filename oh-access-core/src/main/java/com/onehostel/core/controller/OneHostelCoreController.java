package com.onehostel.core.controller;

import java.util.List;

import org.slf4j.MDC;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;

import com.onehostel.core.constants.ServiceConstants;
import com.onehostel.core.dto.CommonRequestObject;
import com.onehostel.core.dto.CommonResponseObject;
import com.onehostel.core.model.helper.OneHostelBaseDoc;
import com.onehostel.core.service.OneHostelCoreService;

import jakarta.validation.Valid;

public abstract class OneHostelCoreController<T extends OneHostelBaseDoc, REQ, RES> {

    protected final OneHostelCoreService<T, REQ, RES> service;

    protected OneHostelCoreController(OneHostelCoreService<T, REQ, RES> service) {
        this.service = service;
    }

    @PostMapping
    public ResponseEntity<CommonResponseObject<RES>> create(
            @Valid @RequestBody CommonRequestObject<REQ> request) {
        RES response = service.create(request);
        return ResponseEntity.ok(CommonResponseObject.success("Record created successfully", response, getCorrelationId()));
    }

    @GetMapping("/{id}")
    public ResponseEntity<CommonResponseObject<RES>> getById(@PathVariable("id") String id) {
        RES response = service.findById(id);
        return ResponseEntity.ok(CommonResponseObject.success("Record fetched successfully", response, getCorrelationId()));
    }

    @PutMapping("/{id}")
    public ResponseEntity<CommonResponseObject<RES>> update(
            @PathVariable("id") String id,
            @Valid @RequestBody CommonRequestObject<REQ> request) {
        RES response = service.update(id, request);
        return ResponseEntity.ok(CommonResponseObject.success("Record updated successfully", response, getCorrelationId()));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<CommonResponseObject<Void>> delete(@PathVariable("id") String id) {
        service.delete(id);
        return ResponseEntity.ok(CommonResponseObject.success("Record deleted successfully", null, getCorrelationId()));
    }

    @GetMapping
    public ResponseEntity<CommonResponseObject<List<RES>>> getAll() {
        List<RES> response = service.findAll();
        return ResponseEntity.ok(CommonResponseObject.success("Records fetched successfully", response, getCorrelationId()));
    }

    protected String getCorrelationId() {
        String correlationId = MDC.get(ServiceConstants.MDC_CORRELATION_ID_KEY);
        return correlationId != null ? correlationId : MDC.get("correlationId");
    }
}