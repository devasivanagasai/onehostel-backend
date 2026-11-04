package com.onehostel.scheduler.service;

import com.onehostel.scheduler.constants.SchedulerEnums.MisfirePolicy;
import com.onehostel.scheduler.vo.JobContext;
import com.onehostel.scheduler.vo.JobResult;

import java.time.Duration;

/**
 * A cluster-safe scheduled job. Implement it as a @Component; scheduler-core seeds its config row,
 * runs it on schedule on exactly one replica per tick, and records the run and any item failures.
 * Raw @Scheduled is not allowed.
 *
 * <pre>
 * &#64;Component
 * public class RentDueReminderJob implements HostelJob {
 *     public String name() { return "rent-due-reminder"; }
 *     public String defaultCron() { return "0 0 9 * * *"; }
 *     public JobResult execute(JobContext ctx) {
 *         for (Invoice invoice : dueInvoices()) {
 *             try { remind(invoice); ctx.recordSuccess(); }
 *             catch (Exception e) { ctx.recordFailure("INVOICE", invoice.getId(), e); }
 *         }
 *         return ctx.result();
 *     }
 * }
 * </pre>
 *
 * A job runs with tenant checks off; iterate organizations with TenantContext.callAs(...) for tenant work.
 */
public interface HostelJob {

    /** Unique, stable name: the config, run history and lock key. */
    String name();

    /** Spring cron (6 fields, seconds first). The DB config row overrides it at runtime. */
    String defaultCron();

    default String defaultZone() {
        return "Asia/Kolkata";
    }

    default MisfirePolicy defaultMisfirePolicy() {
        return MisfirePolicy.RUN_ONCE;
    }

    /** How long one run may hold the lock before another replica may treat it as abandoned. */
    default Duration defaultLockTtl() {
        return Duration.ofMinutes(30);
    }

    JobResult execute(JobContext context);
}
