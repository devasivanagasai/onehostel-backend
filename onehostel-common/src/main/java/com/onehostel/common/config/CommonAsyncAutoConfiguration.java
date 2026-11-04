package com.onehostel.common.config;

import java.util.concurrent.Executor;
import java.util.concurrent.ThreadPoolExecutor;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.aop.interceptor.AsyncUncaughtExceptionHandler;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.scheduling.annotation.AsyncConfigurer;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import com.onehostel.common.helper.CorrelationContext;
import com.onehostel.common.helper.Executors;

/**
 * @Async runs on a bounded executor that carries correlation id and tenant, and a failing void @Async
 * method is logged with its correlation id instead of vanishing. Scheduled work goes through
 * onehostel-scheduler-core, never raw @Scheduled.
 */
@AutoConfiguration
@EnableAsync
public class CommonAsyncAutoConfiguration {

    private static final Logger log = LoggerFactory.getLogger(CommonAsyncAutoConfiguration.class);

    @Bean
    ThreadPoolTaskExecutor asyncExecutor() {
        return Executors.bounded("async-", 4, 16, 200, new ThreadPoolExecutor.CallerRunsPolicy());
    }

    @Bean
    @ConditionalOnMissingBean(AsyncConfigurer.class)
    AsyncConfigurer asyncConfigurer(@Qualifier("asyncExecutor") ObjectProvider<Executor> asyncExecutor) {
        return new AsyncConfigurer() {
            @Override
            public Executor getAsyncExecutor() {
                return asyncExecutor.getObject();
            }

            @Override
            public AsyncUncaughtExceptionHandler getAsyncUncaughtExceptionHandler() {
                return (error, method, params) -> log.error("@Async {}.{} failed (correlationId={}, {} argument(s))",
                        method.getDeclaringClass().getSimpleName(), method.getName(), CorrelationContext.current(),
                        params.length, error);
            }
        };
    }
}
