package com.onehostel.accesscore.service;

import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Service;

import com.onehostel.accesscore.dto.organization.OrganizationRequest;
import com.onehostel.accesscore.dto.organization.OrganizationView;
import com.onehostel.accesscore.model.Hostel;
import com.onehostel.accesscore.model.Organization;
import com.onehostel.accesscore.repository.OrganizationRepository;
import com.onehostel.common.dto.CommonRequestObject;
import com.onehostel.common.dto.ListFilter;
import com.onehostel.common.dto.PageResponse;
import com.onehostel.common.exception.ErrorCode;
import com.onehostel.common.security.AuthenticatedUser;

import lombok.RequiredArgsConstructor;

/**
 * Organizations are the tenant root, so they do not use the HostelAbstractService base layer.
 * Super admins manage all of them; everyone else can only read their own (the one in their
 * token). Another organization's id answers ENTITY_NOT_FOUND, like a missing one.
 */
@Service
@RequiredArgsConstructor
public class OrganizationService {

    private static final String LABEL = "Organization";
    private static final Set<String> SORTABLE = Set.of("name", "createdDateTime", "updatedDateTime");

    private final OrganizationRepository repository;
    private final MongoTemplate mongoTemplate;

    public OrganizationView create(OrganizationRequest request, AuthenticatedUser user) {
        requireSuperAdmin(user);
        String name = request.name().trim();
        if (repository.existsByName(name)) {
            throw ErrorCode.DUPLICATE_RECORD.raiseWithMessage("An organization named \"" + name + "\" already exists.");
        }
        Organization organization = new Organization(name);
        organization.setOwnerUserId(blankToNull(request.ownerUserId()));
        if (request.status() != null) {
            organization.setStatus(request.status());
        }
        return OrganizationView.from(repository.save(organization));
    }

    public OrganizationView update(OrganizationRequest request, AuthenticatedUser user) {
        requireSuperAdmin(user);
        if (request.id() == null || request.id().isBlank()) {
            throw ErrorCode.BAD_REQUEST.raise("id is required to update an organization.");
        }
        Organization organization = find(request.id(), user);
        if (request.version() != null && !request.version().equals(organization.getVersion())) {
            throw ErrorCode.OPTIMISTIC_LOCK_FAILED.raise();
        }
        organization.setName(request.name().trim());
        organization.setOwnerUserId(blankToNull(request.ownerUserId()));
        if (request.status() != null) {
            organization.setStatus(request.status());
        }
        return OrganizationView.from(repository.save(organization));
    }

    public OrganizationView inquire(String id, AuthenticatedUser user) {
        return OrganizationView.from(find(id, user));
    }

    public PageResponse<OrganizationView> getAll(CommonRequestObject<ListFilter> request, AuthenticatedUser user) {
        Criteria criteria = new Criteria();
        if (!user.isSuperAdmin()) {
            criteria = Criteria.where("id").is(requireOrganization(user));
        }
        String keyword = request.requestObject().keyword();
        if (keyword != null && !keyword.isBlank()) {
            criteria = criteria.and("name").regex(Pattern.compile(Pattern.quote(keyword.trim()), Pattern.CASE_INSENSITIVE));
        }
        Pageable pageable = request.toPageable(SORTABLE);
        if (pageable.isUnpaged()) {
            return PageResponse.unpaged(mongoTemplate.find(new Query(criteria).with(pageable.getSort()), Organization.class)
                    .stream().map(OrganizationView::from).toList());
        }
        long total = mongoTemplate.count(new Query(criteria), Organization.class);
        List<OrganizationView> content = mongoTemplate.find(new Query(criteria).with(pageable), Organization.class)
                .stream().map(OrganizationView::from).toList();
        return PageResponse.of(content, pageable.getPageNumber(), pageable.getPageSize(), total);
    }

    /** Refused while hostels still belong to the organization. */
    public void delete(String id, AuthenticatedUser user) {
        requireSuperAdmin(user);
        Organization organization = find(id, user);
        if (mongoTemplate.exists(new Query(Criteria.where("organizationId").is(id)), Hostel.class)) {
            throw ErrorCode.RECORD_IN_USE.raise("Delete or move this organization's hostels first.");
        }
        repository.delete(organization);
    }

    private Organization find(String id, AuthenticatedUser user) {
        if (!user.isSuperAdmin() && !id.equals(user.organizationId())) {
            throw ErrorCode.ENTITY_NOT_FOUND.raise(LABEL, id);
        }
        return repository.findById(id).orElseThrow(() -> ErrorCode.ENTITY_NOT_FOUND.raise(LABEL, id));
    }

    private static void requireSuperAdmin(AuthenticatedUser user) {
        if (!user.isSuperAdmin()) {
            throw ErrorCode.FORBIDDEN.raise();
        }
    }

    private static String requireOrganization(AuthenticatedUser user) {
        if (user.organizationId() == null || user.organizationId().isBlank()) {
            throw ErrorCode.FORBIDDEN.raiseWithMessage("Your account is not linked to an organization.");
        }
        return user.organizationId();
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
