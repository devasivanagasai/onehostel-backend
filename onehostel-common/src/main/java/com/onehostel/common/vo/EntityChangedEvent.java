package com.onehostel.common.vo;

import com.onehostel.common.constants.CommonEnums.Change;
/**
 * Published (Spring application event) after AbstractCrudService commits a change. Listeners use it for
 * best-effort side effects: search indexing, notifications, cache warm-up.
 */
public record EntityChangedEvent(Change change, String entityType, String entityId, String organizationId) {
}
