package com.onehostel.common.config;

import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.Executor;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.data.mongo.MongoDataAutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.cache.CacheManager;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.domain.AuditorAware;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.config.EnableMongoAuditing;
import org.springframework.data.mongodb.core.MongoOperations;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.index.Index;
import org.springframework.data.mongodb.core.mapping.MongoMappingContext;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import com.onehostel.common.exception.DataAccessExceptionHandler;
import com.onehostel.common.helper.AuditAspect;
import com.onehostel.common.helper.CrudSupport;
import com.onehostel.common.helper.Executors;
import com.onehostel.common.helper.FilterCriteriaBuilder;
import com.onehostel.common.helper.UlidIdCallback;
import com.onehostel.common.model.AuditLog;
import com.onehostel.common.service.AuditRecorder;
import com.onehostel.common.vo.CurrentUser;

/** ULID ids, auditing stamps, the audit trail and generic CRUD support for services that use MongoDB. */
@AutoConfiguration(after = MongoDataAutoConfiguration.class)
@ConditionalOnClass(MongoTemplate.class)
@EnableConfigurationProperties({CrudProperties.class, AuditProperties.class})
@EnableMongoAuditing(auditorAwareRef = "currentUserAuditor")
public class CommonMongoAutoConfiguration {

    private static final Logger log = LoggerFactory.getLogger(CommonMongoAutoConfiguration.class);

    @Bean
    DataAccessExceptionHandler dataAccessExceptionHandler(@Value("${spring.application.name:unknown}") String applicationName) {
        return new DataAccessExceptionHandler(applicationName);
    }

    @Bean
    UlidIdCallback ulidIdCallback() {
        return new UlidIdCallback();
    }

    /** createdBy / updatedBy: the caller's user id, "system" outside a request. */
    @Bean
    AuditorAware<String> currentUserAuditor() {
        return () -> Optional.of(CurrentUser.current().map(CurrentUser::auditName).orElse("system"));
    }

    @Bean
    FilterCriteriaBuilder filterCriteriaBuilder(MongoMappingContext mappingContext, CrudProperties properties) {
        return new FilterCriteriaBuilder(mappingContext, properties.zone());
    }

    /** Audit writes are best-effort: when the queue is full the entry is dropped and logged, the request goes on. */
    @Bean
    ThreadPoolTaskExecutor auditExecutor() {
        return Executors.bounded("audit-", 1, 2, 1000,
                (task, executor) -> log.warn("Audit queue full: an audit entry was dropped"));
    }

    @Bean
    AuditRecorder auditRecorder(MongoOperations mongo, @Qualifier("auditExecutor") Executor auditExecutor) {
        return new AuditRecorder(mongo, auditExecutor);
    }

    @Bean
    CrudSupport crudSupport(MongoOperations mongo, CrudProperties properties, FilterCriteriaBuilder filters,
            AuditRecorder audit, ApplicationEventPublisher events, ObjectProvider<CacheManager> cacheManager) {
        return new CrudSupport(mongo, properties, filters, audit, events, cacheManager);
    }

    /** Retention: a TTL index on occurredAt. A changed retention needs the old index dropped first. */
    @Bean
    ApplicationRunner auditRetentionIndex(MongoOperations mongo, AuditProperties properties) {
        return args -> {
            try {
                mongo.indexOps(AuditLog.class).createIndex(new Index()
                        .on("occurredAt", Sort.Direction.ASC)
                        .named("ttl_audit_occurredAt")
                        .expire(Duration.ofDays(properties.retentionDays())));
            } catch (RuntimeException e) {
                log.warn("Could not create the audit retention index ({} days)", properties.retentionDays(), e);
            }
        };
    }

    @Configuration(proxyBeanMethods = false)
    @ConditionalOnClass(name = "org.aspectj.lang.annotation.Aspect")
    static class AuditAspectConfiguration {

        @Bean
        AuditAspect auditAspect(AuditRecorder recorder) {
            return new AuditAspect(recorder);
        }
    }
}
