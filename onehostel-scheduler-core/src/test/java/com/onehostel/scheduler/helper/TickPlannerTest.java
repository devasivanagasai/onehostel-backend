package com.onehostel.scheduler.helper;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;

import org.junit.jupiter.api.Test;
import org.springframework.scheduling.support.CronExpression;

import com.onehostel.scheduler.constants.SchedulerEnums.Action;
import com.onehostel.scheduler.constants.SchedulerEnums.MisfirePolicy;
import com.onehostel.scheduler.helper.TickPlanner.Decision;

class TickPlannerTest {

    private static final CronExpression NINE_AM = CronExpression.parse("0 0 9 * * *");
    private static final ZoneId IST = ZoneId.of("Asia/Kolkata");
    private static final Duration GRACE = Duration.ofMinutes(5);

    /** 09:00 IST on 2026-11-04. */
    private static final Instant TICK = Instant.parse("2026-11-04T03:30:00Z");

    @Test
    void notDueBeforeTheNextTick() {
        Decision decision = TickPlanner.plan(NINE_AM, IST, TICK.minus(Duration.ofDays(1)), TICK.minusSeconds(1),
                MisfirePolicy.RUN_ONCE, GRACE);

        assertThat(decision.action()).isEqualTo(Action.NONE);
    }

    @Test
    void dueTickRuns() {
        Decision decision = TickPlanner.plan(NINE_AM, IST, TICK.minus(Duration.ofDays(1)), TICK.plusSeconds(10),
                MisfirePolicy.SKIP, GRACE);

        assertThat(decision).isEqualTo(new Decision(Action.RUN, TICK));
    }

    @Test
    void alreadyClaimedTickDoesNotRunAgain() {
        Decision decision = TickPlanner.plan(NINE_AM, IST, TICK, TICK.plusSeconds(30), MisfirePolicy.RUN_ONCE, GRACE);

        assertThat(decision.action()).isEqualTo(Action.NONE);
    }

    @Test
    void severalMissedTicksCollapseIntoOneRunForTheLatest() {
        Decision decision = TickPlanner.plan(NINE_AM, IST, TICK.minus(Duration.ofDays(3)), TICK.plus(Duration.ofHours(2)),
                MisfirePolicy.RUN_ONCE, GRACE);

        assertThat(decision).isEqualTo(new Decision(Action.RUN, TICK));
    }

    @Test
    void skipPolicyDropsATickLaterThanTheGrace() {
        Decision decision = TickPlanner.plan(NINE_AM, IST, TICK.minus(Duration.ofDays(1)), TICK.plus(Duration.ofHours(2)),
                MisfirePolicy.SKIP, GRACE);

        assertThat(decision).isEqualTo(new Decision(Action.SKIP, TICK));
    }
}
