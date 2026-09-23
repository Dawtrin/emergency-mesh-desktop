package com.rescue.mesh.network;

import com.rescue.mesh.model.MeshPacket;
import com.rescue.mesh.routing.RoutingEngine;

import java.util.Objects;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * A single-peer, bounded reconnecting sender for Node/Relay routing. At most
 * one packet is awaiting a reconnect retry; subsequent forwarding attempts
 * fail fast rather than accumulating sockets, tasks, or threads.
 */
public final class ReconnectablePacketSender implements RoutingEngine.PacketSender, AutoCloseable {
    private final ScheduledExecutorService scheduler;
    private final ReconnectPolicy policy;
    private final ConnectionStateController.Listener listener;
    private ConnectionStateController active;
    private boolean closed;

    public ReconnectablePacketSender(ReconnectPolicy policy, ConnectionStateController.Listener listener) {
        this.policy = Objects.requireNonNull(policy, "policy");
        this.listener = listener == null ? (state, message) -> {} : listener;
        this.scheduler = Executors.newSingleThreadScheduledExecutor(new DaemonFactory());
    }

    @Override public synchronized boolean send(String host, int port, MeshPacket packet) {
        if (closed) return false;
        if (active != null && (active.getState() == ConnectionState.CONNECTING || active.getState() == ConnectionState.RETRY_WAIT)) {
            listener.onStateChanged(ConnectionState.RETRY_WAIT, "Đang thử lại peer; không xếp thêm gói trùng lặp.");
            return false;
        }
        active = new ConnectionStateController(policy, new ExecutorRetryScheduler(scheduler),
                () -> SocketClient.send(host, port, packet), listener);
        active.connect(); // called only from a bounded network/server worker, never JavaFX thread
        return active.getState() == ConnectionState.CONNECTED;
    }

    public synchronized ConnectionState getState() {
        return active == null ? (closed ? ConnectionState.STOPPED : ConnectionState.DISCONNECTED) : active.getState();
    }

    @Override public synchronized void close() {
        if (closed) return;
        closed = true;
        if (active != null) active.stop();
        scheduler.shutdownNow();
    }

    private static final class DaemonFactory implements ThreadFactory {
        private final AtomicInteger id = new AtomicInteger();
        @Override public Thread newThread(Runnable task) {
            Thread thread = new Thread(task, "Peer-Reconnect-" + id.incrementAndGet());
            thread.setDaemon(true);
            return thread;
        }
    }
}
