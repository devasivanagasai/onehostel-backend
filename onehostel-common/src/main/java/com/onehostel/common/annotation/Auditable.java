package com.onehostel.common.annotation;

import com.onehostel.common.constants.CommonEnums.AuditAction;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Audits a successful call of a service or controller method that is not plain CRUD (approve, transfer,
 * export, ...). The entity id is taken from the returned BaseDocument, else from the first String argument.
 * Create / update / delete through AbstractCrudService are audited with a field diff already: don't add this there.
 */
@Documented
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface Auditable {

    AuditAction action();

    /** EntityType name, e.g. "ADMISSION". */
    String entityType();
}
