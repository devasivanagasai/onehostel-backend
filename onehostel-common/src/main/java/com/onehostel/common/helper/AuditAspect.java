package com.onehostel.common.helper;

import java.util.Arrays;

import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;

import com.onehostel.common.annotation.Auditable;
import com.onehostel.common.model.BaseDocument;
import com.onehostel.common.service.AuditRecorder;

/** The one audit aspect: records {@link Auditable} methods after they succeed. */
@Aspect
public class AuditAspect {

    private final AuditRecorder recorder;

    public AuditAspect(AuditRecorder recorder) {
        this.recorder = recorder;
    }

    @Around("@annotation(auditable)")
    public Object audit(ProceedingJoinPoint joinPoint, Auditable auditable) throws Throwable {
        Object result = joinPoint.proceed();

        String entityId = null;
        String organizationId = null;
        if (result instanceof BaseDocument document) {
            entityId = document.getId();
            organizationId = document.getOrganizationId();
        } else {
            entityId = Arrays.stream(joinPoint.getArgs())
                    .filter(String.class::isInstance)
                    .map(String.class::cast)
                    .findFirst()
                    .orElse(null);
        }
        recorder.record(auditable.action(), auditable.entityType(), entityId, organizationId,
                joinPoint.getSignature().toShortString());
        return result;
    }
}
