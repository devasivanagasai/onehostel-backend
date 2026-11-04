package com.onehostel.scheduler.config;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * app.scheduler.*
 *
 * @param enabled       false stops this replica from running jobs (others still do)
 * @param pollInterval  how often due ticks are checked; also the heartbeat for abandoned runs
 * @param workerThreads how many jobs one replica runs at the same time
 * @param misfireGrace  how late a tick may be and still run under MisfirePolicy.SKIP
 * @param nodeId        lock holder name; defaults to host:pid
 */
@ConfigurationProperties("app.scheduler")
public record SchedulerProperties(
        @DefaultValue("true") boolean enabled,
        @DefaultValue("15s") Duration pollInterval,
        @DefaultValue("4") int workerThreads,
        @DefaultValue("5m") Duration misfireGrace,
        String nodeId) {
}
