package com.onehostel.tenancy.mongo;

import org.springframework.core.Ordered;
import org.springframework.data.mongodb.core.mapping.event.BeforeConvertCallback;

import com.onehostel.tenancy.model.TenantOwned;

/**
 * Sets organizationId on a tenant-owned document before it is written, and refuses one that names
 * a different tenant. Runs on the entity, so the caller's object carries the tenant afterwards.
 */
public class TenantStampingCallback implements BeforeConvertCallback<Object>, Ordered {

    private final TenantBoundary boundary;

    public TenantStampingCallback(TenantBoundary boundary) {
        this.boundary = boundary;
    }

    @Override
    public Object onBeforeConvert(Object entity, String collection) {
        if (entity instanceof TenantOwned owned) {
            String tenantId = boundary.tenantForWrite(collection, owned.getOrganizationId());
            if (tenantId != null && owned.getOrganizationId() == null) {
                owned.setOrganizationId(tenantId);
            }
        }
        return entity;
    }

    @Override
    public int getOrder() {
        // Before callbacks that read organizationId (audit stamping, search indexing)
        return Ordered.HIGHEST_PRECEDENCE + 100;
    }
}
