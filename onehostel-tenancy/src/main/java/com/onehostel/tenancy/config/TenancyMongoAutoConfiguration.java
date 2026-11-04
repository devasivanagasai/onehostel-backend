package com.onehostel.tenancy.config;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.data.mongodb.MongoDatabaseFactory;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.mapping.MongoMappingContext;

import com.onehostel.tenancy.mongo.MongoViolationSink;
import com.onehostel.tenancy.mongo.TenantBoundary;
import com.onehostel.tenancy.mongo.TenantCollectionRegistry;
import com.onehostel.tenancy.mongo.TenantMongoDatabaseFactory;
import com.onehostel.tenancy.mongo.TenantStampingCallback;
import com.onehostel.tenancy.mongo.ViolationSink;

/**
 * Wraps Boot's MongoDatabaseFactory in a TenantMongoDatabaseFactory before MongoTemplate is built from it,
 * so every template, repository and aggregation in the service is tenant-scoped (app.tenancy.mode, ENFORCE by default).
 */
@AutoConfiguration
@ConditionalOnClass(MongoDatabaseFactory.class)
@EnableConfigurationProperties(TenancyProperties.class)
public class TenancyMongoAutoConfiguration {

    @Bean
    TenantCollectionRegistry tenantCollectionRegistry(ObjectProvider<MongoMappingContext> mappingContext) {
        return new TenantCollectionRegistry(mappingContext::getObject);
    }

    @Bean
    ViolationSink tenancyViolationSink(ObjectProvider<MongoDatabaseFactory> factory) {
        return new MongoViolationSink(() -> {
            MongoDatabaseFactory current = factory.getObject();
            return new MongoTemplate(current instanceof TenantMongoDatabaseFactory scoped ? scoped.getDelegate() : current);
        });
    }

    @Bean
    TenantBoundary tenantBoundary(TenancyProperties properties, TenantCollectionRegistry registry, ViolationSink sink) {
        return new TenantBoundary(properties, registry, sink);
    }

    @Bean
    TenantStampingCallback tenantStampingCallback(TenantBoundary boundary) {
        return new TenantStampingCallback(boundary);
    }

    /** Static, and resolving the boundary lazily, so it does not pull other beans in early. */
    @Bean
    static BeanPostProcessor tenantMongoDatabaseFactoryWrapper(ObjectProvider<TenantBoundary> boundary) {
        return new BeanPostProcessor() {
            @Override
            public Object postProcessAfterInitialization(Object bean, String beanName) {
                if (bean instanceof MongoDatabaseFactory factory && !(bean instanceof TenantMongoDatabaseFactory)) {
                    return new TenantMongoDatabaseFactory(factory, boundary.getObject());
                }
                return bean;
            }
        };
    }
}
