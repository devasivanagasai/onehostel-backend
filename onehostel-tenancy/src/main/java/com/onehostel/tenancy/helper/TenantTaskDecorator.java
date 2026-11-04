package com.onehostel.tenancy.helper;

import org.springframework.core.task.TaskDecorator;

import com.onehostel.tenancy.helper.TenantContext.Restore;
import com.onehostel.tenancy.helper.TenantContext.Scope;

/** Carries the submitting thread's tenant into @Async and executor threads. */
public class TenantTaskDecorator implements TaskDecorator {

    @Override
    public Runnable decorate(Runnable runnable) {
        Scope scope = TenantContext.current().orElse(null);
        return () -> {
            try (Restore ignored = TenantContext.enter(scope)) {
                runnable.run();
            }
        };
    }
}
