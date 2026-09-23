package com.rescue.mesh.network;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class ConnectionStateControllerTest {
    @Test
    void retriesWithBoundedBackoffThenConnectsWithoutDuplicateSchedules() {
        FakeScheduler scheduler = new FakeScheduler();
        AtomicInteger sends = new AtomicInteger();
        List<ConnectionState> states = new ArrayList<>();
        ConnectionStateController controller = new ConnectionStateController(
                new ReconnectPolicy(3, 10, 20), scheduler,
                () -> sends.incrementAndGet() == 3, (state, ignored) -> states.add(state));

        controller.connect();
        controller.connect();
        assertEquals(1, sends.get());
        assertEquals(ConnectionState.RETRY_WAIT, controller.getState());
        assertEquals(List.of(10L), scheduler.delays);

        scheduler.runNext();
        assertEquals(2, sends.get());
        assertEquals(List.of(10L, 20L), scheduler.delays);
        scheduler.runNext();
        assertEquals(3, sends.get());
        assertEquals(ConnectionState.CONNECTED, controller.getState());
        assertTrue(states.contains(ConnectionState.CONNECTING));
    }

    @Test
    void stopCancelsRetryAndItCannotResurrect() {
        FakeScheduler scheduler = new FakeScheduler();
        AtomicInteger sends = new AtomicInteger();
        ConnectionStateController controller = new ConnectionStateController(
                new ReconnectPolicy(5, 10, 100), scheduler,
                () -> { sends.incrementAndGet(); return false; }, (state, ignored) -> {});
        controller.connect();
        controller.stop();
        scheduler.runNext();
        assertEquals(1, sends.get());
        assertEquals(ConnectionState.STOPPED, controller.getState());
    }

    @Test
    void finalFailureIsActionableAndBounded() {
        FakeScheduler scheduler = new FakeScheduler();
        AtomicInteger sends = new AtomicInteger();
        List<String> messages = new ArrayList<>();
        ConnectionStateController controller = new ConnectionStateController(
                new ReconnectPolicy(2, 1, 1), scheduler,
                () -> { sends.incrementAndGet(); return false; }, (state, message) -> messages.add(message));
        controller.connect(); scheduler.runNext();
        assertEquals(2, sends.get());
        assertEquals(ConnectionState.FAILED, controller.getState());
        assertTrue(messages.getLast().contains("2 lần thử"));
    }

    private static final class FakeScheduler implements ConnectionStateController.Scheduler {
        private final List<Long> delays = new ArrayList<>();
        private final List<Entry> tasks = new ArrayList<>();
        @Override public ConnectionStateController.Cancellable schedule(Runnable task, long delayMs) {
            Entry entry = new Entry(task); tasks.add(entry); delays.add(delayMs); return () -> entry.cancelled = true;
        }
        void runNext() { Entry entry = tasks.removeFirst(); if (!entry.cancelled) entry.task.run(); }
        private static final class Entry { private final Runnable task; private boolean cancelled; private Entry(Runnable task) { this.task = task; } }
    }
}
