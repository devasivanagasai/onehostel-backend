package com.onehostel.common.model;

import java.time.Instant;
import java.util.Map;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.mapping.Document;

import com.onehostel.common.constants.CommonEnums.AuditAction;
import com.onehostel.tenancy.model.TenantOwned;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * One audited action. Tenant-owned, so each organization reads only its own trail.
 * Kept for app.audit.retention-days (TTL index on occurredAt, created at startup).
 */
@Getter
@Setter
@NoArgsConstructor
@Document(AuditLog.COLLECTION)
@CompoundIndex(name = "idx_audit_org_entity", def = "{'organizationId': 1, 'entityType': 1, 'entityId': 1, 'occurredAt': -1}")
public class AuditLog implements TenantOwned {

    public static final String COLLECTION = "audit_logs";

    /**
     * organizationId of actions that belong to no organization (a super admin signing in, platform
     * settings). Keeps the trail tenant-owned: only an unscoped platform admin reads these entries.
     */
    public static final String PLATFORM_ORGANIZATION = "PLATFORM";

    @Id
    private String id;
    private String organizationId;
    private String entityType;
    private String entityId;
    private AuditAction action;
    private String actorId;
    private String actorUsername;
    /** field path -> before / after, PII masked. Empty for actions without a stored-document diff. */
    private Map<String, FieldChange> changes;
    private String details;
    private String correlationId;
    private String clientIp;
    private Instant occurredAt;

    public record FieldChange(Object before, Object after) {
    }
}
