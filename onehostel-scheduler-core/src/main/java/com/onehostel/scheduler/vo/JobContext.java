package com.onehostel.scheduler.vo;

import java.time.Instant;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.onehostel.scheduler.exception.JobAbortedException;
import com.onehostel.scheduler.model.JobConfig;
import com.onehostel.scheduler.repository.JobStore;

/**
 * Per-run bookkeeping handed to {@link HostelJob#execute}. Count every item with recordSuccess or
 * recordFailure: failures become retryable JobFailure rows, and the abort rule is checked on each failure.
 */
public class JobContext {

    private static final Logger log = LoggerFactory.getLogger(JobContext.class);
    /** The failure-rate rule only applies once this many items were processed. */
    static final int MIN_ITEMS_FOR_FAILURE_RATE = 20;

    private final String jobName;
    private final String runId;
    private final Instant tick;
    private final JobConfig config;
    private final JobStore store;

    private int processed;
    private int succeeded;
    private int failed;
    private int consecutiveFailures;

    public JobContext(String jobName, String runId, Instant tick, JobConfig config, JobStore store) {
        this.jobName = jobName;
        this.runId = runId;
        this.tick = tick;
        this.config = config;
        this.store = store;
    }

    public String jobName() {
        return jobName;
    }

    public String runId() {
        return runId;
    }

    /** The cron fire time this run is for (use it, not now(), for "due by" cut-offs). */
    public Instant tick() {
        return tick;
    }

    public void recordSuccess() {
        processed++;
        succeeded++;
        consecutiveFailures = 0;
    }

    /** Success for an item that may have failed before: its open JobFailure row is resolved. */
    public void recordSuccess(String itemType, String itemId) {
        recordSuccess();
        store.resolveFailure(jobName, itemType, itemId);
    }

    /** Records the item as failed (deduplicated per item, attemptCount incremented) and applies the abort rule. */
    public void recordFailure(String itemType, String itemId, Exception error) {
        processed++;
        failed++;
        consecutiveFailures++;
        log.warn("Job {} run {}: {} {} failed", jobName, runId, itemType, itemId, error);
        store.recordFailure(jobName, itemType, itemId, error, runId);
        checkAbortRule();
    }

    /** Ids of items of this type whose last attempt failed, for targeted retry. */
    public List<String> openFailures(String itemType) {
        return store.openFailureIds(jobName, itemType);
    }

    public JobResult result() {
        return new JobResult(processed, succeeded, failed, null);
    }

    private void checkAbortRule() {
        if (config.getMaxConsecutiveFailures() > 0 && consecutiveFailures >= config.getMaxConsecutiveFailures()) {
            throw new JobAbortedException(consecutiveFailures + " consecutive failures");
        }
        if (config.getMaxFailureRate() > 0 && processed >= MIN_ITEMS_FOR_FAILURE_RATE
                && (double) failed / processed > config.getMaxFailureRate()) {
            throw new JobAbortedException(failed + " of " + processed + " items failed");
        }
    }
}
