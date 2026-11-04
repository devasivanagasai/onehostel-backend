package com.onehostel.scheduler.config;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.data.mongo.MongoDataAutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.data.mongodb.core.MongoOperations;

import com.onehostel.scheduler.repository.JobStore;
import com.onehostel.scheduler.service.HostelJob;
import com.onehostel.scheduler.service.JobScheduler;

@AutoConfiguration(after = MongoDataAutoConfiguration.class)
@ConditionalOnProperty(prefix = "app.scheduler", name = "enabled", matchIfMissing = true)
@EnableConfigurationProperties(SchedulerProperties.class)
public class SchedulerAutoConfiguration {

    @Bean
    JobStore jobStore(MongoOperations mongo) {
        return new JobStore(mongo);
    }

    @Bean
    JobScheduler jobScheduler(ObjectProvider<HostelJob> jobs, JobStore store, SchedulerProperties properties) {
        return new JobScheduler(jobs.orderedStream().toList(), store, properties);
    }
}
