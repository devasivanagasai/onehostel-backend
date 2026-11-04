package com.onehostel.resident.constants;

import com.onehostel.common.constants.EntityTypeSpec;
import com.onehostel.common.model.BaseDocument;

/** Every enum of the resident service, in one place. Import the nested type, e.g. {@code import com.onehostel.resident.constants.ResidentEnums.EntityType;}. */
public final class ResidentEnums {

    private ResidentEnums() {
    }

    /**
     * Document types of this service, for generic CRUD, messages and audit records.
     * Each feature adds one entry, e.g. {@code ROOM(Room.class, "Room")}.
     */
    public enum EntityType implements EntityTypeSpec {
        ;

        private final Class<? extends BaseDocument> entityClass;
        private final String displayName;

        EntityType(Class<? extends BaseDocument> entityClass, String displayName) {
            this.entityClass = entityClass;
            this.displayName = displayName;
        }

        @Override
        public Class<? extends BaseDocument> entityClass() {
            return entityClass;
        }

        @Override
        public String displayName() {
            return displayName;
        }
    }
}
