package com.onehostel.service.helper;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.regex.Pattern;

import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;

import com.onehostel.dto.helper.CommonRequestObject;
import com.onehostel.dto.helper.HostelAbstractDto;
import com.onehostel.dto.helper.ListFilter;
import com.onehostel.dto.helper.PageResponse;
import com.onehostel.exception.ErrorCode;
import com.onehostel.model.helper.HostelAbstractDocument;
import com.onehostel.security.AuthenticatedUser;

public abstract class HostelAbstractService<E extends HostelAbstractDocument, D extends HostelAbstractDto, V>
        implements HostelAbstractInterface<E, D, V> {

    private static final String FIELD_ID = "id";
    private static final String FIELD_ORGANIZATION = "organizationId";
    private static final String FIELD_BRANCH = "hostelBranchId";

    protected final MongoTemplate mongoTemplate;
    private final Class<E> entityClass;

    protected HostelAbstractService(MongoTemplate mongoTemplate, Class<E> entityClass) {
        this.mongoTemplate = mongoTemplate;
        this.entityClass = entityClass;
    }

    public V create(D dto, AuthenticatedUser user) {
        requireWriteRole(user);
        String organizationId = organizationForCreate(dto, user);
        validate(dto, null, organizationId);

        E entity = dtoToEntity(dto);
        entity.setId(null);
        entity.setVersion(null);
        entity.setOrganizationId(organizationId);
        return entityToVO(mongoTemplate.insert(entity));
    }

    public V update(D dto, AuthenticatedUser user) {
        requireWriteRole(user);
        if (dto.id() == null || dto.id().isBlank()) {
            throw ErrorCode.BAD_REQUEST.raise("id is required to update a " + entityLabel() + ".");
        }
        E existing = findScoped(dto.id(), user);
        if (dto.version() != null && !dto.version().equals(existing.getVersion())) {
            throw ErrorCode.OPTIMISTIC_LOCK_FAILED.raise();
        }
        validate(dto, existing, existing.getOrganizationId());

        updateEntity(dto, existing);
        return entityToVO(mongoTemplate.save(existing));
    }

    public V inquire(String id, AuthenticatedUser user) {
        return entityToVO(findScoped(id, user));
    }

    public PageResponse<V> getAll(CommonRequestObject<ListFilter> request, AuthenticatedUser user) {
        Criteria criteria = combine(listCriteria(request.requestObject(), user));
        Pageable pageable = request.toPageable(sortableFields());

        if (pageable.isUnpaged()) {
            Query query = new Query(criteria).with(pageable.getSort());
            return PageResponse.unpaged(mongoTemplate.find(query, entityClass).stream().map(this::entityToVO).toList());
        }
        long total = mongoTemplate.count(new Query(criteria), entityClass);
        List<V> content = mongoTemplate.find(new Query(criteria).with(pageable), entityClass).stream()
                .map(this::entityToVO)
                .toList();
        return PageResponse.of(content, pageable.getPageNumber(), pageable.getPageSize(), total);
    }

    public void delete(String id, AuthenticatedUser user) {
        requireWriteRole(user);
        mongoTemplate.remove(findScoped(id, user));
    }

    /** The document with this id inside the user's scope, or ENTITY_NOT_FOUND. */
    protected E findScoped(String id, AuthenticatedUser user) {
        List<Criteria> parts = new ArrayList<>(tenantCriteria(user));
        parts.add(Criteria.where(FIELD_ID).is(id));
        E entity = mongoTemplate.findOne(new Query(combine(parts)), entityClass);
        if (entity == null) {
            throw ErrorCode.ENTITY_NOT_FOUND.raise(entityLabel(), id);
        }
        return entity;
    }

    /** Restricts a query to what the user may see. Empty for SUPER_ADMIN. */
    protected List<Criteria> tenantCriteria(AuthenticatedUser user) {
        if (user.isSuperAdmin()) {
            return List.of();
        }
        return List.of(Criteria.where(FIELD_ORGANIZATION).is(requireOrganization(user)));
    }

    private List<Criteria> listCriteria(ListFilter filter, AuthenticatedUser user) {
        List<Criteria> parts = new ArrayList<>(tenantCriteria(user));
        if (filter.hostelBranchId() != null && !filter.hostelBranchId().isBlank()) {
            parts.add(Criteria.where(FIELD_BRANCH).is(filter.hostelBranchId()));
        }
        String keyword = filter.keyword();
        if (keyword != null && !keyword.isBlank() && !keywordFields().isEmpty()) {
            Pattern pattern = Pattern.compile(Pattern.quote(keyword.trim()), Pattern.CASE_INSENSITIVE);
            parts.add(new Criteria().orOperator(
                    keywordFields().stream().map(field -> Criteria.where(field).regex(pattern)).toList()));
        }
        return parts;
    }

    private String organizationForCreate(D dto, AuthenticatedUser user) {
        String requested = dto.organizationId();
        if (user.isSuperAdmin()) {
            if (requested == null || requested.isBlank()) {
                throw ErrorCode.BAD_REQUEST.raise(
                        "organizationId is required when an admin creates a " + entityLabel() + ".");
            }
            return requested;
        }
        String own = requireOrganization(user);
        if (requested != null && !requested.isBlank() && !Objects.equals(requested, own)) {
            throw ErrorCode.FORBIDDEN.raise();
        }
        return own;
    }

    private void requireWriteRole(AuthenticatedUser user) {
        if (!writeRoles().contains(user.role())) {
            throw ErrorCode.FORBIDDEN.raise();
        }
    }

    private static String requireOrganization(AuthenticatedUser user) {
        if (user.organizationId() == null || user.organizationId().isBlank()) {
            throw ErrorCode.FORBIDDEN.raiseWithMessage("Your account is not linked to an organization.");
        }
        return user.organizationId();
    }

    private static Criteria combine(List<Criteria> parts) {
        if (parts.isEmpty()) {
            return new Criteria();
        }
        return parts.size() == 1 ? parts.get(0) : new Criteria().andOperator(parts);
    }
}
