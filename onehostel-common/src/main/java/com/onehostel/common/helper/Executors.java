package com.onehostel.common.helper;

import java.util.List;
import java.util.concurrent.RejectedExecutionHandler;

import org.springframework.core.task.support.CompositeTaskDecorator;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import com.onehostel.tenancy.helper.TenantTaskDecorator;

/**
 * Named, bounded executors that carry the correlation id and tenant into their threads.
 * Use it for every executor a service defines (e.g. a notificationExecutor), returned from a @Bean
 * method so Spring initializes it and drains it on shutdown.
 */
public final class Executors {

    private Executors() {
    }

    public static ThreadPoolTaskExecutor bounded(String threadPrefix, int core, int max, int queue,
            RejectedExecutionHandler rejection) {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setThreadNamePrefix(threadPrefix);
        executor.setCorePoolSize(core);
        executor.setMaxPoolSize(max);
        executor.setQueueCapacity(queue);
        executor.setRejectedExecutionHandler(rejection);
        executor.setTaskDecorator(new CompositeTaskDecorator(List.of(new MdcTaskDecorator(), new TenantTaskDecorator())));
        // Drain on shutdown: queued work is finished, not dropped
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(30);
        return executor;
    }
}
