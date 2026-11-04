package com.onehostel.common.controller;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;

import com.onehostel.common.annotation.ResponseMessage;
import com.onehostel.common.dto.CommonRequestObject;
import com.onehostel.common.dto.CommonResponseObject;
import com.onehostel.common.dto.PageResponse;
import com.onehostel.common.model.BaseDocument;
import com.onehostel.common.service.AbstractCrudService;

import jakarta.validation.Valid;

/**
 * The six standard endpoints. No try/catch: errors go to GlobalExceptionHandler; bodies are wrapped by
 * ResponseEnvelopeAdvice. A concrete controller adds only a @RequestMapping and feature-specific endpoints:
 *
 * <pre>
 * &#64;RestController
 * &#64;RequestMapping("/api/v1/property/room")
 * &#64;RequiredArgsConstructor
 * public class RoomController extends AbstractCrudController&lt;Room&gt; {
 *     private final RoomService roomService;
 *     &#64;Override protected AbstractCrudService&lt;Room&gt; getService() { return roomService; }
 * }
 * </pre>
 */
public abstract class AbstractCrudController<T extends BaseDocument> {

    protected abstract AbstractCrudService<T> getService();

    @GetMapping("/inq/{id}")
    public T getById(@PathVariable String id) {
        return getService().getById(id);
    }

    /** Paging, sort and issuanceSearch filters come from the envelope; an empty body lists page 0. */
    @PostMapping("/getall")
    public PageResponse<T> getAll(@Valid @RequestBody(required = false) CommonRequestObject<?> request) {
        return getService().getAll(request);
    }

    @PostMapping("/create")
    @ResponseStatus(HttpStatus.CREATED)
    @ResponseMessage("Created successfully")
    public T create(@Valid @RequestBody T entity) {
        return getService().create(entity);
    }

    @PutMapping("/update/{id}")
    @ResponseMessage("Updated successfully")
    public T update(@PathVariable String id, @Valid @RequestBody T entity) {
        return getService().update(id, entity);
    }

    /** Partial update: not bean-validated as a whole, since required fields may be absent. */
    @PatchMapping("/patch/{id}")
    @ResponseMessage("Updated successfully")
    public T patch(@PathVariable String id, @RequestBody T entity) {
        return getService().patch(id, entity);
    }

    @DeleteMapping("/delete/{id}")
    public CommonResponseObject<Void> delete(@PathVariable String id) {
        getService().delete(id);
        return CommonResponseObject.ok("Deleted successfully", null);
    }
}
