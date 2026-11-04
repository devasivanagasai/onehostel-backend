package com.onehostel.tenancy.mongo;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

import org.springframework.data.mongodb.core.mapping.MongoMappingContext;
import org.springframework.data.mongodb.core.mapping.MongoPersistentEntity;

import com.onehostel.tenancy.annotation.GlobalCollection;
import com.onehostel.tenancy.model.TenantOwned;

/**
 * Which collections hold tenant-owned documents: those mapped to a {@link TenantOwned} class without
 * {@link GlobalCollection}. Read from the mapping context, which registers an entity before
 * MongoTemplate first touches its collection.
 */
public class TenantCollectionRegistry {

    private final Supplier<MongoMappingContext> mappingContext;
    private final Set<String> tenantCollections = ConcurrentHashMap.newKeySet();

    public TenantCollectionRegistry(Supplier<MongoMappingContext> mappingContext) {
        this.mappingContext = mappingContext;
    }

    public boolean isTenantCollection(String collectionName) {
        if (tenantCollections.contains(collectionName)) {
            return true;
        }
        for (MongoPersistentEntity<?> entity : mappingContext.get().getPersistentEntities()) {
            Class<?> type = entity.getType();
            if (TenantOwned.class.isAssignableFrom(type) && !type.isAnnotationPresent(GlobalCollection.class)
                    && collectionName.equals(entity.getCollection())) {
                tenantCollections.add(collectionName);
                return true;
            }
        }
        return false;
    }
}
