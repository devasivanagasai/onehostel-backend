package com.onehostel.scheduler.service;

import java.lang.management.ManagementFactory;
import java.net.InetAddress;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.context.SmartLifecycle;
import org.springframework.scheduling.concurrent.CustomizableThreadFactory;
import org.springframework.scheduling.support.CronExpression;

import com.onehostel.scheduler.config.SchedulerProperties;
import com.onehostel.scheduler.constants.SchedulerEnums.RunState;
import com.onehostel.scheduler.exception.JobAbortedException;
import com.onehostel.scheduler.helper.TickPlanner.Decision;
import com.onehostel.scheduler.helper.TickPlanner;
import com.onehostel.scheduler.model.JobConfig;
import com.onehostel.scheduler.model.JobLock;
import com.onehostel.scheduler.model.JobRun;
import com.onehostel.scheduler.repository.JobStore;
import com.onehostel.scheduler.vo.JobContext;
import com.onehostel.scheduler.vo.JobResult;
import com.onehostel.tenancy.helper.TenantContext;

/**
 * Runs every HostelJob bean. One poll loop per replica reads each job's config row (cron, zone, enabled are
 * editable at runtime), works out the due tick from the fire cursor, and runs it if this replica wins the
 * lease. The same loop is the heartbeat that marks lapsed RUNNING runs ABANDONED.
 */
public class JobScheduler implements SmartLifecycle {

    private static final Logger log = LoggerFactory.getLogger(JobScheduler.class);

    private final List<HostelJob> jobs;
    private final JobStore store;
    private final SchedulerProperties properties;
    private final String holder;

    private ScheduledExecutorService poller;
    private ExecutorService workers;
    private volatile boolean running;
    private Instant startedAt;

    public JobScheduler(List<HostelJob> jobs, JobStore store, SchedulerProperties properties) {
        this.jobs = List.copyOf(jobs);
        this.store = store;
        this.properties = properties;
        this.holder = properties.nodeId() != null ? properties.nodeId() : defaultNodeId();
    }

    @Override
    public void start() {
        startedAt = Instant.now();
        jobs.forEach(store::seedConfig);
        poller = Executors.newSingleThreadScheduledExecutor(new CustomizableThreadFactory("job-poller-"));
        workers = Executors.newFixedThreadPool(properties.workerThreads(), new CustomizableThreadFactory("job-"));
        long interval = properties.pollInterval().toMillis();
        poller.scheduleWithFixedDelay(this::poll, interval, interval, TimeUnit.MILLISECONDS);
        running = true;
        log.info("Scheduler started on {} with {} job(s): {}", holder, jobs.size(), jobs.stream().map(HostelJob::name).toList());
    }

    @Override
    public void stop() {
        running = false;
        if (poller != null) {
            poller.shutdownNow();
        }
        if (workers != null) {
            workers.shutdown();
            try {
                if (!workers.awaitTermination(30, TimeUnit.SECONDS)) {
                    log.warn("Jobs still running at shutdown; their runs will be marked ABANDONED when the lock lapses");
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }

    @Override
    public boolean isRunning() {
        return running;
    }

    /** Runs a job now, outside its schedule (e.g. from an admin endpoint). False when it is already running. */
    public boolean triggerNow(String jobName) {
        HostelJob job = jobs.stream().filter(j -> j.name().equals(jobName)).findFirst()
                .orElseThrow(() -> new IllegalArgumentException("No job named " + jobName));
        JobConfig config = store.config(jobName).orElseThrow();
        Instant tick = Instant.now();
        if (!store.tryClaim(jobName, tick, holder, Duration.ofSeconds(config.getLockTtlSeconds()))) {
            return false;
        }
        workers.submit(() -> run(job, config, tick));
        return true;
    }

    private void poll() {
        // Never let an exception escape: it would cancel the scheduled poll for good
        try {
            store.markAbandoned().forEach(run -> alert(run.getJobName(), run.getId(), RunState.ABANDONED, run.getMessage()));
            for (HostelJob job : jobs) {
                pollJob(job);
            }
        } catch (RuntimeException e) {
            log.error("Scheduler poll failed", e);
        }
    }

    private void pollJob(HostelJob job) {
        Optional<JobConfig> found = store.config(job.name());
        if (found.isEmpty() || !found.get().isEnabled()) {
            return;
        }
        JobConfig config = found.get();
        CronExpression cron;
        try {
            cron = CronExpression.parse(config.getCron());
        } catch (IllegalArgumentException e) {
            log.error("Job {} has an invalid cron '{}' in {}; not scheduling it", job.name(), config.getCron(), JobConfig.COLLECTION);
            return;
        }
        Optional<JobLock> lock = store.lock(job.name());
        Instant cursor = lock.map(JobLock::getLastTick).orElse(startedAt);
        Decision decision = TickPlanner.plan(cron, ZoneId.of(config.getZone()), cursor, Instant.now(),
                config.getMisfirePolicy(), properties.misfireGrace());

        switch (decision.action()) {
            case NONE -> { }
            case SKIP -> {
                if (store.advanceCursor(job.name(), decision.tick())) {
                    store.recordSkipped(job.name(), decision.tick(), RunState.SKIPPED_MISFIRE, "Missed tick skipped (misfire policy SKIP)");
                    log.info("Job {} skipped missed tick {}", job.name(), decision.tick());
                }
            }
            case RUN -> {
                Duration ttl = Duration.ofSeconds(config.getLockTtlSeconds());
                if (store.tryClaim(job.name(), decision.tick(), holder, ttl)) {
                    workers.submit(() -> run(job, config, decision.tick()));
                } else if (heldByAnotherRun(job.name()) && store.advanceCursor(job.name(), decision.tick())) {
                    // The previous run is still going: this tick is skipped, not queued behind it
                    store.recordSkipped(job.name(), decision.tick(), RunState.SKIPPED_LOCKED, "Previous run still holds the lock");
                    log.warn("Job {} tick {} skipped: previous run still running", job.name(), decision.tick());
                }
            }
        }
    }

    private boolean heldByAnotherRun(String jobName) {
        return store.lock(jobName)
                .map(lock -> lock.getLockedUntil() != null && lock.getLockedUntil().isAfter(Instant.now()))
                .orElse(false);
    }

    private void run(HostelJob job, JobConfig config, Instant tick) {
        JobRun run = store.startRun(job.name(), tick, holder, Duration.ofSeconds(config.getLockTtlSeconds()));
        RunState state = RunState.FAILED;
        try (MDC.MDCCloseable ignored = MDC.putCloseable("correlationId", "job-" + run.getId())) {
            JobContext context = new JobContext(job.name(), run.getId(), tick, config, store);
            try {
                // Jobs work across tenants; tenant-specific work goes through TenantContext.callAs
                JobResult result = TenantContext.callUnscoped("scheduled job " + job.name(), () -> job.execute(context));
                result = result != null ? result : context.result();
                state = result.failed() > 0 ? RunState.FAILED : RunState.SUCCESS;
                fill(run, result);
            } catch (JobAbortedException e) {
                state = RunState.ABORTED;
                fill(run, context.result());
                run.setMessage("Aborted: " + e.getMessage());
            } catch (RuntimeException e) {
                log.error("Job {} run {} threw", job.name(), run.getId(), e);
                fill(run, context.result());
                run.setMessage(e.getClass().getSimpleName() + ": " + e.getMessage());
            }
        } finally {
            run.setState(state);
            store.finishRun(run);
            store.release(job.name(), holder);
            log.info("Job {} run {} for tick {} ended {} ({} processed, {} failed)", job.name(), run.getId(), tick, state,
                    run.getProcessed(), run.getFailed());
            if (state != RunState.SUCCESS) {
                alert(job.name(), run.getId(), state, run.getMessage());
            }
        }
    }

    private static void fill(JobRun run, JobResult result) {
        run.setProcessed(result.processed());
        run.setSucceeded(result.succeeded());
        run.setFailed(result.failed());
        run.setMessage(result.message());
    }

    /** Until onehostel-external-core can send mail, alerts are ERROR log lines (which log monitoring picks up). */
    private void alert(String jobName, String runId, RunState state, String message) {
        List<String> recipients = store.config(jobName).map(JobConfig::getAlertEmails).orElse(List.of());
        log.error("ALERT job {} run {} ended {}: {} (alert emails: {})", jobName, runId, state, message, recipients);
    }

    private static String defaultNodeId() {
        try {
            return InetAddress.getLocalHost().getHostName() + ":" + ManagementFactory.getRuntimeMXBean().getPid();
        } catch (Exception e) {
            return "node:" + ManagementFactory.getRuntimeMXBean().getPid();
        }
    }
}
