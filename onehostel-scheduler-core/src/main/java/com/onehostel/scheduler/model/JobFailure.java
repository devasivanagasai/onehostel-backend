package com.onehostel.scheduler.model;

import java.time.Instant;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.mapping.Document;

import lombok.Data;

/**
 * An item a job could not process, one row per (job, item type, item id) however often it fails.
 * Open rows are the recovery list: JobContext.openFailures() lets the job retry exactly those items.
 */
@Data
@Document(JobFailure.COLLECTION)
@CompoundIndex(name = "idx_failure_open", def = "{'jobName': 1, 'itemType': 1, 'resolved': 1}")
public class JobFailure {

    public static final String COLLECTION = "scheduler_job_failure";

    /** jobName:itemType:itemId */
    @Id
    private String id;
    private String jobName;
    private String itemType;
    private String itemId;
    private int attemptCount;
    private String lastError;
    private String lastRunId;
    private Instant firstFailedAt;
    private Instant lastFailedAt;
    private boolean resolved;
    private Instant resolvedAt;

    public static String idOf(String jobName, String itemType, String itemId) {
        return jobName + ":" + itemType + ":" + itemId;
    }
}
