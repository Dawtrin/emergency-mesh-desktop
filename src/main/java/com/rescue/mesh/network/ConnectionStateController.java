package com.rescue.mesh.network;

import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Small transport-agnostic state machine. It deliberately owns at most one
 * scheduled retry, making it safe to test with a fake scheduler and safe to
 * stop during desktop shutdown.
 */
public final class ConnectionStateController implements AutoCloseable {
    @FunctionalInterface public interface Sender { boolean trySend(); }
    @FunctionalInterface public interface Scheduler { Cancellable schedule(Runnable task, long delayMs); }
    public interface Cancellable { void cancel(); }
    @FunctionalInterface public interface Listener { void onStateChanged(ConnectionState state, String message); }

    private final ReconnectPolicy policy;
    private final Scheduler scheduler;
    private final Sender sender;
    private final Listener listener;
    private final AtomicBoolean stopped = new AtomicBoolean();
    private ConnectionState state = ConnectionState.DISCONNECTED;
    private Cancellable retry;
    private int attempts;

    public ConnectionStateController(ReconnectPolicy policy, Scheduler scheduler, Sender sender, Listener listener) {
        this.policy = Objects.requireNonNull(policy, "policy");
        this.scheduler = Objects.requireNonNull(scheduler, "scheduler");
        this.sender = Objects.requireNonNull(sender, "sender");
        this.listener = listener == null ? (s, m) -> {} : listener;
    }

    public synchronized ConnectionState getState() { return state; }
    public synchronized int getAttempts() { return attempts; }

    /** Starts one bounded delivery sequence. Repeated calls never create extra retries. */
    public synchronized void connect() {
        if (stopped.get() || state == ConnectionState.CONNECTING || state == ConnectionState.RETRY_WAIT) return;
        attempts = 0;
        attempt();
    }

    private void attempt() {
        if (stopped.get()) return;
        transition(ConnectionState.CONNECTING, "Đang kết nối tới peer...");
        attempts++;
        boolean delivered;
        try { delivered = sender.trySend(); } catch (RuntimeException e) { delivered = false; }
        if (stopped.get()) return;
        if (delivered) {
            retry = null;
            transition(ConnectionState.CONNECTED, "Đã gửi tới peer; đang chờ xác nhận nếu cần.");
            return;
        }
        if (attempts >= policy.maxAttempts()) {
            retry = null;
            transition(ConnectionState.FAILED, "Không thể kết nối peer sau " + attempts + " lần thử.");
            return;
        }
        long delay = policy.delayForAttempt(attempts);
        transition(ConnectionState.RETRY_WAIT, "Không kết nối được; sẽ thử lại sau " + delay + " ms.");
        retry = scheduler.schedule(() -> {
            synchronized (ConnectionStateController.this) {
                retry = null;
                if (!stopped.get()) attempt();
            }
        }, delay);
    }

    public synchronized void stop() {
        if (!stopped.compareAndSet(false, true)) return;
        if (retry != null) { retry.cancel(); retry = null; }
        transition(ConnectionState.STOPPED, "Đã dừng kết nối; các lần thử lại đã bị hủy.");
    }

    @Override public void close() { stop(); }

    private void transition(ConnectionState next, String message) {
        state = next;
        listener.onStateChanged(next, message);
    }
}
