package com.onehostel.scheduler.model;

import com.onehostel.scheduler.constants.SchedulerEnums.RunState;

import java.time.Instant;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.mapping.Document;

import lombok.Data;

/** History of one run (or skipped tick) of a job. */
@Data
@Document(JobRun.COLLECTION)
@CompoundIndex(name = "idx_run_job_tick", def = "{'jobName': 1, 'tick': -1}")
public class JobRun {

    public static final String COLLECTION = "scheduler_job_run";

    @Id
    private String id;
    private String jobName;
    /** The cron fire time this run is for. */
    private Instant tick;
    private RunState state;
    private String holder;
    private Instant startedAt;
    /** When the lock of a RUNNING run lapses; after that the heartbeat marks it ABANDONED. */
    private Instant expiresAt;
    private Instant finishedAt;
    private int processed;
    private int succeeded;
    private int failed;
    private String message;
}
