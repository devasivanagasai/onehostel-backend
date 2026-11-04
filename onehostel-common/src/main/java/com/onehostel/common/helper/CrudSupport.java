package com.onehostel.common.helper;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.cache.CacheManager;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.mongodb.core.MongoOperations;

import com.onehostel.common.config.CrudProperties;
import com.onehostel.common.service.AuditRecorder;

/**
 * Everything AbstractCrudService needs, injected as one bean so concrete services need no constructor
 * of their own (and can still use constructor injection for their extra dependencies).
 */
public record CrudSupport(
        MongoOperations mongo,
        CrudProperties properties,
        FilterCriteriaBuilder filters,
        AuditRecorder audit,
        ApplicationEventPublisher events,
        ObjectProvider<CacheManager> cacheManager) {
}
