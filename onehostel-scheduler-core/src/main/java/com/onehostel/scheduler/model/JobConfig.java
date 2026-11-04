package com.onehostel.scheduler.model;

import java.util.List;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import com.onehostel.scheduler.constants.SchedulerEnums.MisfirePolicy;

import lombok.Data;

/** Runtime-editable settings of one job, seeded from the HostelJob defaults the first time it starts. */
@Data
@Document(JobConfig.COLLECTION)
public class JobConfig {

    public static final String COLLECTION = "scheduler_job_config";

    /** The job name. */
    @Id
    private String name;
    private String cron;
    private String zone;
    private boolean enabled;
    private MisfirePolicy misfirePolicy;
    private long lockTtlSeconds;
    /** Abort a run after this many failures in a row. */
    private int maxConsecutiveFailures;
    /** Abort a run when more than this share of at least 20 items failed (0.5 = half). */
    private double maxFailureRate;
    /** Who is told about FAILED, ABORTED and ABANDONED runs. */
    private List<String> alertEmails;
}
