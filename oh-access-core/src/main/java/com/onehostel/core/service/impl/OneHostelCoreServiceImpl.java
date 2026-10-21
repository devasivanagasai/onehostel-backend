package com.onehostel.core.service.impl;

import java.util.List;

import com.onehostel.core.context.UserContextHolder;
import com.onehostel.core.dto.CommonRequestObject;
import com.onehostel.core.exception.ErrorCode;
import com.onehostel.core.model.CommonSearch;
import com.onehostel.core.model.helper.OneHostelBaseDoc;
import com.onehostel.core.model.helper.SearchableDocument;
import com.onehostel.core.model.helper.TenantAwareDocument;
import com.onehostel.core.repository.OneHostelRepository;
import com.onehostel.core.service.CommonSearchService;
import com.onehostel.core.service.OneHostelCoreService;

public abstract class OneHostelCoreServiceImpl<T extends OneHostelBaseDoc, REQ, RES>
        implements OneHostelCoreService<T, REQ, RES> {

    protected final OneHostelRepository<T, String> repository;
    protected final CommonSearchService commonSearchService;

    protected OneHostelCoreServiceImpl(OneHostelRepository<T, String> repository) {
        this(repository, null);
    }

    protected OneHostelCoreServiceImpl(OneHostelRepository<T, String> repository, CommonSearchService commonSearchService) {
        this.repository = repository;
        this.commonSearchService = commonSearchService;
    }

    protected abstract T toEntity(REQ requestDto);

    protected abstract RES toResponseDto(T entity);

    protected abstract void updateEntityFromRequest(T entity, REQ requestDto);

    @Override
    public RES create(CommonRequestObject<REQ> request) {
        if (request.getRequestObject() == null) {
            throw ErrorCode.MALFORMED_REQUEST.raiseWithMessage("Payload body cannot be null");
        }

        T entity = toEntity(request.getRequestObject());

        if (entity instanceof TenantAwareDocument tenantDoc) {
            String orgId = UserContextHolder.getOrganizationId();
            if (orgId != null && !orgId.isBlank()) {
                tenantDoc.setOrganizationId(orgId);
            }
        }

        T savedEntity = repository.save(entity);

        if (commonSearchService != null && savedEntity instanceof SearchableDocument searchable) {
            commonSearchService.sync(searchable.getSearchObject());
        }

        return toResponseDto(savedEntity);
    }

    @Override
    public RES findById(String id) {
        T entity = repository.findByIdAndIsDeletedFalse(id)
                .orElseThrow(() -> ErrorCode.ENTITY_NOT_FOUND.raiseWithMessage("Record not found with id: " + id));

        validateTenantAccess(entity);
        return toResponseDto(entity);
    }

    @Override
    public RES update(String id, CommonRequestObject<REQ> request) {
        if (request.getRequestObject() == null) {
            throw ErrorCode.MALFORMED_REQUEST.raiseWithMessage("Payload body cannot be null");
        }

        T existingEntity = repository.findByIdAndIsDeletedFalse(id)
                .orElseThrow(() -> ErrorCode.ENTITY_NOT_FOUND.raiseWithMessage("Record not found with id: " + id));

        validateTenantAccess(existingEntity);
        updateEntityFromRequest(existingEntity, request.getRequestObject());

        T updatedEntity = repository.save(existingEntity);

        if (commonSearchService != null && updatedEntity instanceof SearchableDocument searchable) {
            commonSearchService.sync(searchable.getSearchObject());
        }

        return toResponseDto(updatedEntity);
    }

    @Override
    public void delete(String id) {
        T entity = repository.findByIdAndIsDeletedFalse(id)
                .orElseThrow(() -> ErrorCode.ENTITY_NOT_FOUND.raiseWithMessage("Record not found with id: " + id));

        validateTenantAccess(entity);
        entity.markDeleted(UserContextHolder.getUserId());
        repository.save(entity);

        if (commonSearchService != null && entity instanceof SearchableDocument searchable) {
            CommonSearch cs = searchable.getSearchObject();
            if (cs != null && cs.getEntityModelKey() != null && cs.getEntityEnum() != null) {
                commonSearchService.remove(cs.getEntityModelKey(), cs.getEntityEnum());
            }
        }
    }

    @Override
    public List<RES> findAll() {
        return repository.findAllByIsDeletedFalse().stream()
                .filter(this::isTenantAuthorized)
                .map(this::toResponseDto)
                .toList();
    }

    protected void validateTenantAccess(T entity) {
        if (entity instanceof TenantAwareDocument tenantDoc) {
            String currentOrg = UserContextHolder.getOrganizationId();
            if (currentOrg != null && tenantDoc.getOrganizationId() != null
                    && !currentOrg.equals(tenantDoc.getOrganizationId())) {
                throw ErrorCode.FORBIDDEN.raiseWithMessage("Cross-tenant access violation");
            }
        }
    }

    private boolean isTenantAuthorized(T entity) {
        if (entity instanceof TenantAwareDocument tenantDoc) {
            String currentOrg = UserContextHolder.getOrganizationId();
            return currentOrg == null || currentOrg.equals(tenantDoc.getOrganizationId());
        }
        return true;
    }
}