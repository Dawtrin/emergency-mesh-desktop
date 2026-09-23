package com.rescue.mesh.network;

import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

/** Adapter kept separate so tests can inject a deterministic scheduler. */
public final class ExecutorRetryScheduler implements ConnectionStateController.Scheduler {
    private final ScheduledExecutorService executor;
    public ExecutorRetryScheduler(ScheduledExecutorService executor) { this.executor = executor; }
    @Override public ConnectionStateController.Cancellable schedule(Runnable task, long delayMs) {
        ScheduledFuture<?> future = executor.schedule(task, delayMs, TimeUnit.MILLISECONDS);
        return () -> future.cancel(true);
    }
}
