package com.onehostel.scheduler.constants;

/** Every enum of scheduler-core, in one place. Import the nested type, e.g. {@code import com.onehostel.scheduler.constants.SchedulerEnums.MisfirePolicy;}. */
public final class SchedulerEnums {

    private SchedulerEnums() {
    }

    /** What to do with ticks missed while no replica was running (deploy, outage). */
    public enum MisfirePolicy {
        /** Run once for the latest missed tick, however late. */
        RUN_ONCE,
        /** Skip ticks missed by more than the grace period; wait for the next one. */
        SKIP
    }

    public enum RunState {
        RUNNING,
        SUCCESS,
        /** Finished, but some items failed or the job threw. */
        FAILED,
        /** The previous run still held the lock when this tick came due. */
        SKIPPED_LOCKED,
        /** Missed by more than the grace period under MisfirePolicy.SKIP. */
        SKIPPED_MISFIRE,
        /** Stopped by the abort rule (failure rate or consecutive failures). */
        ABORTED,
        /** Its lock expired while RUNNING: the replica died or the run overran its TTL. */
        ABANDONED
    }

    public enum Action {
        NONE,
        RUN,
        SKIP
    }
}
