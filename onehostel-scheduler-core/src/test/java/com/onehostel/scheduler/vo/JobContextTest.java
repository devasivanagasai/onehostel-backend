package com.onehostel.scheduler.vo;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import java.time.Instant;

import org.junit.jupiter.api.Test;

import com.onehostel.scheduler.exception.JobAbortedException;
import com.onehostel.scheduler.model.JobConfig;
import com.onehostel.scheduler.repository.JobStore;

class JobContextTest {

    private final JobStore store = mock(JobStore.class);

    private JobContext context(int maxConsecutive, double maxRate) {
        JobConfig config = new JobConfig();
        config.setMaxConsecutiveFailures(maxConsecutive);
        config.setMaxFailureRate(maxRate);
        return new JobContext("rent-due-reminder", "run-1", Instant.now(), config, store);
    }

    @Test
    void countsItemsAndRecordsFailures() {
        JobContext context = context(10, 0.5);
        IllegalStateException error = new IllegalStateException("SMS gateway down");

        context.recordSuccess();
        context.recordFailure("INVOICE", "inv-1", error);
        context.recordSuccess("INVOICE", "inv-2");

        assertThat(context.result()).isEqualTo(new JobResult(3, 2, 1, null));
        verify(store).recordFailure("rent-due-reminder", "INVOICE", "inv-1", error, "run-1");
        verify(store).resolveFailure("rent-due-reminder", "INVOICE", "inv-2");
    }

    @Test
    void abortsAfterTooManyConsecutiveFailures() {
        JobContext context = context(3, 0);
        RuntimeException error = new RuntimeException("down");

        context.recordFailure("INVOICE", "1", error);
        context.recordFailure("INVOICE", "2", error);
        assertThatThrownBy(() -> context.recordFailure("INVOICE", "3", error))
                .isInstanceOf(JobAbortedException.class)
                .hasMessageContaining("3 consecutive failures");
    }

    @Test
    void abortsWhenTheFailureRateIsTooHighOnceEnoughItemsRan() {
        JobContext context = context(0, 0.5);
        RuntimeException error = new RuntimeException("bad data");

        // Exactly half failed after 20 items: at the limit, not over it
        for (int i = 0; i < 10; i++) {
            context.recordSuccess();
            context.recordFailure("INVOICE", "f" + i, error);
        }
        assertThatThrownBy(() -> context.recordFailure("INVOICE", "last", error))
                .isInstanceOf(JobAbortedException.class)
                .hasMessageContaining("11 of 21 items failed");
    }
}
