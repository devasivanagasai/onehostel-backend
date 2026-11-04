package com.onehostel.scheduler.model;

import java.time.Instant;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import lombok.Data;

/**
 * Lease lock and fire cursor of one job. A replica takes the lease for a tick only if it is free and the
 * tick is newer than lastTick, so each tick runs once across all replicas. Only the holder releases it.
 */
@Data
@Document(JobLock.COLLECTION)
public class JobLock {

    public static final String COLLECTION = "scheduler_job_lock";

    /** The job name. */
    @Id
    private String name;
    private String holder;
    private Instant lockedUntil;
    /** The latest tick claimed (run or deliberately skipped). */
    private Instant lastTick;
}
