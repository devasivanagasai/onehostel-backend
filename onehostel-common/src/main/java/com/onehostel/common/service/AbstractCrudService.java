package com.onehostel.common.service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Set;

import org.bson.Document;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.mapping.PropertyHandler;
import org.springframework.data.mongodb.core.MongoOperations;
import org.springframework.data.mongodb.core.mapping.MongoPersistentEntity;
import org.springframework.data.mongodb.core.mapping.MongoPersistentProperty;
import org.springframework.data.mongodb.core.query.BasicQuery;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;

import com.mongodb.client.result.UpdateResult;
import com.onehostel.common.config.CrudProperties;
import com.onehostel.common.constants.CommonEnums.AuditAction;
import com.onehostel.common.constants.CommonEnums.Change;
import com.onehostel.common.constants.CommonEnums.SortOrder;
import com.onehostel.common.constants.EntityTypeSpec;
import com.onehostel.common.dto.CommonRequestObject;
import com.onehostel.common.dto.PageResponse;
import com.onehostel.common.exception.CommonErrorCode;
import com.onehostel.common.helper.CrudSupport;
import com.onehostel.common.model.BaseDocument;
import com.onehostel.common.vo.CurrentUser;
import com.onehostel.common.vo.EntityChangedEvent;
import com.onehostel.tenancy.helper.TenantContext;

/**
 * Generic CRUD for one document type. A concrete service names its EntityType and overrides hooks only:
 *
 * <pre>
 * &#64;Service
 * public class RoomService extends AbstractCrudService&lt;Room&gt; {
 *     &#64;Override protected EntityTypeSpec getEntityType() { return EntityType.ROOM; }
 *     &#64;Override protected void beforeCreate(Room room) { ... domain rules, throw ErrorCode.X.raise(...) ... }
 * }
 * </pre>
 *
 * Tenancy: reads and writes are scoped by onehostel-tenancy; create stamps the caller's tenant and refuses
 * a body naming another one; ownership and audit fields are never taken from a request body.
 * Updates are $set of the mapped business fields (PUT also unsets fields left out; PATCH leaves them),
 * with optimistic locking when the body carries a version. Deletes are soft.
 * Use wrapper types (Integer, not int) for fields a PATCH may omit, or the omitted value overwrites with 0.
 */
public abstract class AbstractCrudService<T extends BaseDocument> {

    private static final Set<String> SERVER_OWNED = Set.of("id", "organizationId", "parentOrgIds", "createdBy",
            "createdAt", "updatedBy", "updatedAt", "version", "deleted", "deletedAt", "deletedBy");

    private CrudSupport crud;

    /** Method injection, so subclasses need no constructor for the base's dependencies. */
    @Autowired
    void setCrudSupport(CrudSupport crud) {
        this.crud = crud;
    }

    protected abstract EntityTypeSpec getEntityType();

    // --- hooks ---

    /** Domain rules before insert, e.g. uniqueness checks. The tenant is already set. */
    protected void beforeCreate(T entity) {
    }

    protected void afterCreate(T entity) {
    }

    /** Domain rules before an update or patch; existing is the stored record. */
    protected void beforeUpdate(T existing, T incoming) {
    }

    protected void afterUpdate(T entity) {
    }

    protected void beforeDelete(T existing) {
    }

    /** Enrich a record read through getById. */
    protected void afterGetById(T entity) {
    }

    /** Enrich a page read through getAll. */
    protected void afterGetAll(List<T> entities) {
    }

    /** Feature-specific list criteria from the envelope (e.g. its requestObject), or null. */
    protected Criteria additionalCriteria(CommonRequestObject<?> request) {
        return null;
    }

    /** Caches holding this entity, cleared after every change. Declare them in the service's CacheSpec. */
    protected Collection<String> cacheNames() {
        return List.of();
    }

    // --- operations ---

    public T getById(String id) {
        T entity = load(id);
        afterGetById(entity);
        return entity;
    }

    public PageResponse<T> getAll(CommonRequestObject<?> request) {
        CrudProperties properties = crud.properties();
        int page = request != null && request.page() != null ? Math.max(0, request.page()) : 0;
        int size = request != null && request.size() != null
                ? Math.min(Math.max(1, request.size()), properties.maxPageSize())
                : properties.defaultPageSize();

        List<Criteria> conditions = new ArrayList<>();
        conditions.add(notDeleted());
        if (request != null && request.issuanceSearch() != null && !request.issuanceSearch().isEmpty()) {
            conditions.add(crud.filters().build(request.issuanceSearch(), entityClass()));
        }
        Criteria additional = additionalCriteria(request);
        if (additional != null) {
            conditions.add(additional);
        }
        Criteria criteria = new Criteria().andOperator(conditions);

        Query query = Query.query(criteria).with(PageRequest.of(page, size, sort(request)));
        List<T> content = mongo().find(query, entityClass());
        long total = mongo().count(Query.query(criteria), entityClass());
        afterGetAll(content);
        return PageResponse.of(content, page, size, total);
    }

    public T create(T entity) {
        resetServerOwnedFields(entity);
        assignTenant(entity);
        beforeCreate(entity);
        T saved = mongo().insert(entity);
        afterCreate(saved);
        changed(Change.CREATED, saved);
        crud.audit().recordChange(AuditAction.CREATE, getEntityType().name(), null, saved);
        return saved;
    }

    /** PUT: every business field takes the body's value; fields the body leaves out are removed. */
    public T update(String id, T incoming) {
        return applyUpdate(id, incoming, true);
    }

    /** PATCH: only fields present in the body change. */
    public T patch(String id, T incoming) {
        return applyUpdate(id, incoming, false);
    }

    public void delete(String id) {
        T existing = load(id);
        beforeDelete(existing);
        Instant now = Instant.now();
        String actor = actor();
        Update update = new Update()
                .set("deleted", true).set("deletedAt", now).set("deletedBy", actor)
                .set("updatedAt", now).set("updatedBy", actor)
                .inc("version", 1);
        mongo().updateFirst(Query.query(Criteria.where("_id").is(id).and("deleted").ne(true)), update, entityClass());
        changed(Change.DELETED, existing);
        crud.audit().recordChange(AuditAction.DELETE, getEntityType().name(), existing, null);
    }

    // --- helpers for subclasses ---

    protected MongoOperations mongo() {
        return crud.mongo();
    }

    @SuppressWarnings("unchecked")
    protected Class<T> entityClass() {
        return (Class<T>) getEntityType().entityClass();
    }

    /** True when a non-deleted record matches the query (tenant-scoped like every other read). */
    protected boolean exists(Query query) {
        Document matchLive = new Document("$and", List.of(query.getQueryObject(), notDeleted().getCriteriaObject()));
        return mongo().exists(new BasicQuery(matchLive), entityClass());
    }

    /** Clears this entity's caches. A plain call, not @CacheEvict, so it also works from inside the class. */
    protected void evictCache() {
        CacheManager cacheManager = crud.cacheManager().getIfAvailable();
        if (cacheManager == null) {
            return;
        }
        for (String name : cacheNames()) {
            Cache cache = cacheManager.getCache(name);
            if (cache != null) {
                cache.clear();
            }
        }
    }

    // --- internals ---

    private T load(String id) {
        T entity = mongo().findOne(Query.query(Criteria.where("_id").is(id).and("deleted").ne(true)), entityClass());
        if (entity == null) {
            throw CommonErrorCode.ENTITY_NOT_FOUND.raise(getEntityType().displayName(), id);
        }
        requireAccess(entity);
        return entity;
    }

    private T applyUpdate(String id, T incoming, boolean replace) {
        T existing = load(id);
        beforeUpdate(existing, incoming);

        Update update = mappedUpdate(incoming, replace)
                .set("updatedAt", Instant.now())
                .set("updatedBy", actor())
                .inc("version", 1);
        Criteria match = Criteria.where("_id").is(id).and("deleted").ne(true);
        if (incoming.getVersion() != null) {
            match = match.and("version").is(incoming.getVersion());
        }
        UpdateResult result = mongo().updateFirst(Query.query(match), update, entityClass());
        if (result.getMatchedCount() == 0) {
            throw incoming.getVersion() != null
                    ? CommonErrorCode.OPTIMISTIC_LOCK_FAILED.raise()
                    : CommonErrorCode.ENTITY_NOT_FOUND.raise(getEntityType().displayName(), id);
        }
        // Re-read: the stored document, not the request, is what the caller and the audit diff see
        T updated = load(id);
        afterUpdate(updated);
        changed(Change.UPDATED, updated);
        crud.audit().recordChange(AuditAction.UPDATE, getEntityType().name(), existing, updated);
        return updated;
    }

    /** $set of the body's mapped business fields; with replace, $unset of those it left out. */
    private Update mappedUpdate(T incoming, boolean replace) {
        Document written = new Document();
        mongo().getConverter().write(incoming, written);
        MongoPersistentEntity<?> entity = mongo().getConverter().getMappingContext().getRequiredPersistentEntity(entityClass());

        Update update = new Update();
        entity.doWithProperties((PropertyHandler<MongoPersistentProperty>) property -> {
            if (property.isIdProperty() || property.isVersionProperty() || SERVER_OWNED.contains(property.getName())) {
                return;
            }
            String field = property.getFieldName();
            if (written.containsKey(field)) {
                update.set(field, written.get(field));
            } else if (replace) {
                update.unset(field);
            }
        });
        return update;
    }

    private void assignTenant(T entity) {
        if (TenantContext.isUnscoped()) {
            // Platform admin: must say which organization the record belongs to
            if (entity.getOrganizationId() == null || entity.getOrganizationId().isBlank()) {
                throw CommonErrorCode.BAD_REQUEST.raise("organizationId is required");
            }
            return;
        }
        String tenantId = TenantContext.require();
        if (entity.getOrganizationId() != null && !tenantId.equals(entity.getOrganizationId())) {
            throw CommonErrorCode.SECURITY_BREACH.raise();
        }
        entity.setOrganizationId(tenantId);
        entity.setParentOrgIds(TenantContext.ancestors());
    }

    /** Defence in depth: the tenancy library already scopes the query; this catches OFF/SHADOW collections. */
    private void requireAccess(T entity) {
        if (TenantContext.isUnscoped()) {
            return;
        }
        TenantContext.current().ifPresent(scope -> {
            if (!scope.readableOrganizationIds().contains(entity.getOrganizationId())) {
                throw CommonErrorCode.SECURITY_BREACH.raise();
            }
        });
    }

    private static void resetServerOwnedFields(BaseDocument entity) {
        entity.setId(null);
        entity.setCreatedBy(null);
        entity.setCreatedAt(null);
        entity.setUpdatedBy(null);
        entity.setUpdatedAt(null);
        entity.setVersion(null);
        entity.setDeleted(false);
        entity.setDeletedAt(null);
        entity.setDeletedBy(null);
    }

    private Sort sort(CommonRequestObject<?> request) {
        if (request == null || request.sortField() == null || request.sortField().isBlank()) {
            return Sort.by(Sort.Direction.DESC, "createdAt");
        }
        crud.filters().validateField(entityClass(), request.sortField());
        Sort.Direction direction = request.sortOrder() == SortOrder.ASC ? Sort.Direction.ASC : Sort.Direction.DESC;
        return Sort.by(direction, request.sortField());
    }

    private void changed(Change change, T entity) {
        evictCache();
        crud.events().publishEvent(new EntityChangedEvent(change, getEntityType().name(), entity.getId(), entity.getOrganizationId()));
    }

    private static Criteria notDeleted() {
        return Criteria.where("deleted").ne(true);
    }

    private static String actor() {
        return CurrentUser.current().map(CurrentUser::auditName).orElse("system");
    }
}
