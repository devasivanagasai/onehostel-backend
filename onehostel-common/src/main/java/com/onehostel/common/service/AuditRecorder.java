package com.onehostel.common.service;

import java.time.Instant;
import java.util.Map;
import java.util.concurrent.Executor;

import org.bson.Document;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.mongodb.core.MongoOperations;

import com.onehostel.common.constants.CommonEnums.AuditAction;
import com.onehostel.common.helper.AuditDiff;
import com.onehostel.common.helper.CorrelationContext;
import com.onehostel.common.helper.UlidIdCallback;
import com.onehostel.common.model.AuditLog.FieldChange;
import com.onehostel.common.model.AuditLog;
import com.onehostel.common.model.BaseDocument;
import com.onehostel.common.vo.CurrentUser;
import com.onehostel.tenancy.helper.TenantContext;

/**
 * Writes AuditLog entries on the auditExecutor. Best-effort by design: an audit write that fails is
 * logged with its stack trace and never fails the request that caused it. The actor and diff are
 * captured on the caller's thread; the executor carries the tenant and correlation id across.
 */
public class AuditRecorder {

    private static final Logger log = LoggerFactory.getLogger(AuditRecorder.class);

    private final MongoOperations mongo;
    private final Executor auditExecutor;

    public AuditRecorder(MongoOperations mongo, Executor auditExecutor) {
        this.mongo = mongo;
        this.auditExecutor = auditExecutor;
    }

    /** A change to a stored document. before is null for CREATE; after is null for DELETE. */
    public void recordChange(AuditAction action, String entityType, BaseDocument before, BaseDocument after) {
        BaseDocument subject = after != null ? after : before;
        Map<String, FieldChange> changes = action == AuditAction.DELETE
                ? Map.of("deleted", new FieldChange(false, true))
                : AuditDiff.diff(toDocument(before), toDocument(after));
        submit(entry(action, entityType, subject.getId(), subject.getOrganizationId(), changes, null));
    }

    /** An action without a document diff (approve, login, export, ...). */
    public void record(AuditAction action, String entityType, String entityId, String organizationId, String details) {
        submit(entry(action, entityType, entityId, organizationId, Map.of(), details));
    }

    private AuditLog entry(AuditAction action, String entityType, String entityId, String organizationId,
            Map<String, FieldChange> changes, String details) {
        AuditLog entry = new AuditLog();
        entry.setId(UlidIdCallback.newId());
        entry.setAction(action);
        entry.setEntityType(entityType);
        entry.setEntityId(entityId);
        entry.setOrganizationId(organizationId != null ? organizationId
                : TenantContext.tenantId().orElse(AuditLog.PLATFORM_ORGANIZATION));
        entry.setChanges(changes);
        entry.setDetails(details);
        entry.setCorrelationId(CorrelationContext.current());
        entry.setOccurredAt(Instant.now());
        CurrentUser.current().ifPresent(user -> {
            entry.setActorId(user.auditName());
            entry.setActorUsername(user.username());
            entry.setClientIp(user.clientIp());
        });
        if (entry.getActorId() == null) {
            entry.setActorId("system");
        }
        return entry;
    }

    private void submit(AuditLog entry) {
        try {
            auditExecutor.execute(() -> write(entry));
        } catch (RuntimeException e) {
            // Executor saturated or shutting down: the audit entry is lost, the request is not
            log.warn("Audit entry dropped: {} {} {}", entry.getAction(), entry.getEntityType(), entry.getEntityId(), e);
        }
    }

    private void write(AuditLog entry) {
        try {
            // Written in the entry's own organization, which may be a different one than the caller's
            // when a super admin (unscoped) acts on a tenant record
            // when a super admin (unscoped) acts on a tenant record. Every entry has one (PLATFORM_ORGANIZATION
            // at the least), so the tenant boundary stays enforced for audit_logs.
            if (TenantContext.isUnscoped()) {
                mongo.insert(entry);
            } else {
                TenantContext.callAs(entry.getOrganizationId(), TenantContext.ancestors(), () -> mongo.insert(entry));
            }
        } catch (RuntimeException e) {
            log.warn("Audit write failed: {} {} {}", entry.getAction(), entry.getEntityType(), entry.getEntityId(), e);
        }
    }

    private Document toDocument(BaseDocument entity) {
        if (entity == null) {
            return new Document();
        }
        Document document = new Document();
        mongo.getConverter().write(entity, document);
        return document;
    }
}
