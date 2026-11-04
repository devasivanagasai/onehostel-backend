package com.onehostel.common.exception;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class ErrorCodeSpecTest {

    @Test
    void formatsTheTemplateWithArguments() {
        assertThat(CommonErrorCode.ENTITY_NOT_FOUND.raise("Room", "01J9").getMessage()).isEqualTo("Room not found: 01J9");
    }

    @Test
    void withoutArgumentsKeepsTheTemplate() {
        assertThat(CommonErrorCode.OPTIMISTIC_LOCK_FAILED.raise().getMessage())
                .isEqualTo("Record was updated by another request. Reload and retry.");
    }

    @Test
    void causedByKeepsTheCauseOutOfTheMessage() {
        IllegalStateException cause = new IllegalStateException("socket closed");
        HostelException e = CommonErrorCode.SERVICE_UNAVAILABLE.raiseCausedBy(cause);

        assertThat(e.getCause()).isSameAs(cause);
        assertThat(e.getMessage()).isEqualTo("Service is temporarily unavailable.");
    }

    @Test
    void contextIsKeptForLogsOnly() {
        HostelException e = CommonErrorCode.STATE_CONFLICT.raise("Bed is occupied").with("bedId", "B1");

        assertThat(e.getContext()).containsEntry("bedId", "B1");
        assertThat(e.getMessage()).isEqualTo("Bed is occupied");
    }
}
