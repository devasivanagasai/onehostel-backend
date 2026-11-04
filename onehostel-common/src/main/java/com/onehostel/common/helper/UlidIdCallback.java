package com.onehostel.common.helper;

import org.springframework.core.Ordered;
import org.springframework.data.mongodb.core.mapping.event.BeforeConvertCallback;

import com.github.f4b6a3.ulid.UlidCreator;
import com.onehostel.common.model.BaseDocument;

/**
 * Assigns a ULID to every new BaseDocument: sortable by creation time, 26 characters, no ObjectId or UUID.
 * Human-readable business numbers (ADM-2026-000123) come from a counters collection, not from this id.
 */
public class UlidIdCallback implements BeforeConvertCallback<BaseDocument>, Ordered {

    @Override
    public BaseDocument onBeforeConvert(BaseDocument entity, String collection) {
        if (entity.getId() == null) {
            entity.setId(newId());
        }
        return entity;
    }

    public static String newId() {
        return UlidCreator.getMonotonicUlid().toString();
    }

    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE;
    }
}
