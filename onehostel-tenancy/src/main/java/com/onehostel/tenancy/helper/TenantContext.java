package com.onehostel.tenancy.helper;

import com.onehostel.tenancy.exception.TenantBoundaryViolationException;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;

/**
 * The tenant (organization) the current thread works for. Set by TenantResolutionFilter for web
 * requests, by {@link #callAs} for jobs and consumers, and carried into @Async threads by
 * TenantTaskDecorator. Every tenant-owned Mongo operation reads it through TenantBoundary.
 */
public final class TenantContext {

    private static final ThreadLocal<Scope> CURRENT = new ThreadLocal<>();

    private TenantContext() {
    }

    /**
     * @param tenantId       the organization the caller acts for; null for an unscoped scope
     * @param ancestors      the tenant's ancestor chain (group -> hostel -> branch), readable but not writable
     * @param unscopedReason why tenant checks are off (platform admin, cross-tenant job); null when scoped
     */
    public record Scope(String tenantId, List<String> ancestors, String unscopedReason) {

        public Scope {
            ancestors = ancestors == null ? List.of() : List.copyOf(ancestors);
        }

        public static Scope of(String tenantId, List<String> ancestors) {
            if (tenantId == null || tenantId.isBlank()) {
                throw new IllegalArgumentException("tenantId is required for a scoped context");
            }
            return new Scope(tenantId, ancestors, null);
        }

        public static Scope unscoped(String reason) {
            if (reason == null || reason.isBlank()) {
                throw new IllegalArgumentException("An unscoped context needs a reason");
            }
            return new Scope(null, List.of(), reason);
        }

        public boolean isUnscoped() {
            return unscopedReason != null;
        }

        /** The tenant plus its ancestors: the organizations whose records the caller may read. */
        public List<String> readableOrganizationIds() {
            List<String> ids = new ArrayList<>(ancestors.size() + 1);
            ids.add(tenantId);
            ids.addAll(ancestors);
            return ids;
        }
    }

    public static Optional<Scope> current() {
        return Optional.ofNullable(CURRENT.get());
    }

    public static Optional<String> tenantId() {
        return current().map(Scope::tenantId);
    }

    /** The tenant id; fails closed when the thread has none (public request, job without callAs). */
    public static String require() {
        return tenantId().orElseThrow(() -> new TenantBoundaryViolationException("No tenant in the current context"));
    }

    public static List<String> ancestors() {
        return current().map(Scope::ancestors).orElse(List.of());
    }

    public static boolean isUnscoped() {
        return current().map(Scope::isUnscoped).orElse(false);
    }

    /** Runs work as the given tenant, restoring the previous context afterwards. */
    public static <T> T callAs(String tenantId, List<String> ancestors, Supplier<T> work) {
        return callIn(Scope.of(tenantId, ancestors), work);
    }

    public static void runAs(String tenantId, List<String> ancestors, Runnable work) {
        callAs(tenantId, ancestors, () -> {
            work.run();
            return null;
        });
    }

    /** Runs work with tenant checks off. The reason is recorded with any boundary decision. */
    public static <T> T callUnscoped(String reason, Supplier<T> work) {
        return callIn(Scope.unscoped(reason), work);
    }

    public static void runUnscoped(String reason, Runnable work) {
        callUnscoped(reason, () -> {
            work.run();
            return null;
        });
    }

    /** Runs work in a captured scope (null = no tenant), e.g. one taken on another thread. */
    public static <T> T callIn(Scope scope, Supplier<T> work) {
        try (Restore ignored = enter(scope)) {
            return work.get();
        }
    }

    /**
     * Switches the thread to a scope (null = no tenant) until the returned handle is closed, which restores
     * the previous one. For filters and interceptors: {@code try (var t = TenantContext.enter(scope)) { ... }}
     */
    public static Restore enter(Scope scope) {
        Scope previous = CURRENT.get();
        set(scope);
        return () -> set(previous);
    }

    /** Restores the previous scope when closed. */
    @FunctionalInterface
    public interface Restore extends AutoCloseable {
        @Override
        void close();
    }

    private static void set(Scope scope) {
        if (scope == null) {
            CURRENT.remove();
        } else {
            CURRENT.set(scope);
        }
    }
}
