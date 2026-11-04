package com.onehostel.tenancy.mongo;

import java.util.Optional;

import org.bson.Document;
import org.springframework.lang.Nullable;

import com.onehostel.tenancy.config.TenancyProperties;
import com.onehostel.tenancy.constants.TenancyEnums.TenancyMode;
import com.onehostel.tenancy.exception.TenantBoundaryViolationException;
import com.onehostel.tenancy.helper.TenantContext.Scope;
import com.onehostel.tenancy.helper.TenantContext;
import com.onehostel.tenancy.model.TenantOwned;

/**
 * Decides, per collection operation, how the current tenant restricts it.
 * Reads may see the tenant and its ancestors; writes only the tenant itself.
 */
public class TenantBoundary {

    private final TenancyProperties properties;
    private final TenantCollectionRegistry registry;
    private final ViolationSink violationSink;

    public TenantBoundary(TenancyProperties properties, TenantCollectionRegistry registry, ViolationSink violationSink) {
        this.properties = properties;
        this.registry = registry;
        this.violationSink = violationSink;
    }

    /** OFF for collections without a tenant-owned document class; otherwise the configured mode. */
    public TenancyMode modeFor(String collectionName) {
        return registry.isTenantCollection(collectionName) ? properties.modeFor(collectionName) : TenancyMode.OFF;
    }

    /**
     * The filter to AND into this operation, empty when none applies (OFF, SHADOW, unscoped context).
     * In ENFORCE an operation without a tenant fails; in SHADOW it is recorded and allowed.
     *
     * @param originalFilter the caller's filter, used only to spot SHADOW queries that lack organizationId
     */
    public Optional<Document> tenantFilter(String collectionName, String operation, @Nullable Object originalFilter,
            boolean write) {

        TenancyMode mode = modeFor(collectionName);
        if (mode == TenancyMode.OFF) {
            return Optional.empty();
        }
        Optional<Scope> scope = TenantContext.current();
        if (scope.map(Scope::isUnscoped).orElse(false)) {
            return Optional.empty();
        }
        if (mode == TenancyMode.SHADOW) {
            if (scope.isEmpty()) {
                violationSink.record(collectionName, operation, null, "no tenant in context");
            } else if (!(originalFilter instanceof Document filter && filter.containsKey(TenantOwned.TENANT_FIELD))) {
                violationSink.record(collectionName, operation, scope.get().tenantId(), "filter has no organizationId");
            }
            return Optional.empty();
        }
        Scope current = scope.orElseThrow(() -> new TenantBoundaryViolationException(
                operation + " on " + collectionName + " without a tenant in context"));
        return Optional.of(write
                ? new Document(TenantOwned.TENANT_FIELD, current.tenantId())
                : new Document(TenantOwned.TENANT_FIELD, new Document("$in", current.readableOrganizationIds())));
    }

    /**
     * The organizationId a new document must carry, after checking the value it already has.
     * Null when the collection is not tenant-scoped, or the context is unscoped and the document says who owns it.
     */
    @Nullable
    public String tenantForWrite(String collectionName, @Nullable String documentOrgId) {
        TenancyMode mode = modeFor(collectionName);
        if (mode == TenancyMode.OFF) {
            return null;
        }
        Optional<Scope> scope = TenantContext.current();
        if (scope.map(Scope::isUnscoped).orElse(false)) {
            if (documentOrgId == null && mode == TenancyMode.ENFORCE) {
                throw new TenantBoundaryViolationException(
                        "A " + collectionName + " document written outside a tenant must carry organizationId");
            }
            return null;
        }
        if (scope.isEmpty()) {
            if (mode == TenancyMode.ENFORCE) {
                throw new TenantBoundaryViolationException("write to " + collectionName + " without a tenant in context");
            }
            violationSink.record(collectionName, "insert", null, "no tenant in context");
            return null;
        }
        String tenantId = scope.get().tenantId();
        if (documentOrgId != null && !documentOrgId.equals(tenantId)) {
            if (mode == TenancyMode.ENFORCE) {
                throw new TenantBoundaryViolationException(
                        "write to " + collectionName + " for organization " + documentOrgId + " by tenant " + tenantId);
            }
            violationSink.record(collectionName, "insert", tenantId, "document belongs to " + documentOrgId);
        }
        return tenantId;
    }
}
