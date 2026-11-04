package com.onehostel.scheduler.helper;

import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;

import org.springframework.scheduling.support.CronExpression;

import com.onehostel.scheduler.constants.SchedulerEnums.Action;
import com.onehostel.scheduler.constants.SchedulerEnums.MisfirePolicy;

/** Decides, from the cron and the fire cursor, whether a job is due now and for which tick. */
public final class TickPlanner {
    /** Guards against a runaway loop on a sub-second cron after a very long outage. */
    private static final int MAX_STEPS = 100_000;

    public record Decision(Action action, Instant tick) {
        static final Decision NONE = new Decision(Action.NONE, null);
    }

    private TickPlanner() {
    }

    /**
     * @param lastTick the latest tick already claimed (the cursor)
     * @param grace    how late a tick may be and still run under MisfirePolicy.SKIP
     * @return RUN or SKIP for the LATEST due tick (older missed ticks collapse into it), else NONE
     */
    public static Decision plan(CronExpression cron, ZoneId zone, Instant lastTick, Instant now, MisfirePolicy policy,
            Duration grace) {
        ZonedDateTime due = cron.next(lastTick.atZone(zone));
        if (due == null || due.toInstant().isAfter(now)) {
            return Decision.NONE;
        }
        ZonedDateTime latest = due;
        for (int i = 0; i < MAX_STEPS; i++) {
            ZonedDateTime next = cron.next(latest);
            if (next == null || next.toInstant().isAfter(now)) {
                break;
            }
            latest = next;
        }
        boolean late = Duration.between(latest.toInstant(), now).compareTo(grace) > 0;
        return new Decision(policy == MisfirePolicy.SKIP && late ? Action.SKIP : Action.RUN, latest.toInstant());
    }
}
