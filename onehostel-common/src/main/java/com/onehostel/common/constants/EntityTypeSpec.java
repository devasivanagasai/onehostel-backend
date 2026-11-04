package com.onehostel.common.constants;

import com.onehostel.common.model.BaseDocument;
/**
 * One entry of a service's EntityType enum: the document class behind a feature and its display name,
 * used in messages ("Room not found: 01J...") and audit records.
 */
public interface EntityTypeSpec {

    Class<? extends BaseDocument> entityClass();

    String displayName();

    /** Stable name for audit records and events (the enum constant). */
    String name();
}
