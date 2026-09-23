package com.rescue.mesh.network;

/** Bounded retry policy for an outbound peer. */
public record ReconnectPolicy(int maxAttempts, long initialDelayMs, long maxDelayMs) {
    public ReconnectPolicy {
        if (maxAttempts < 1) throw new IllegalArgumentException("maxAttempts must be at least 1");
        if (initialDelayMs < 0) throw new IllegalArgumentException("initialDelayMs must not be negative");
        if (maxDelayMs < initialDelayMs) throw new IllegalArgumentException("maxDelayMs must be >= initialDelayMs");
    }

    public long delayForAttempt(int failedAttempt) {
        if (failedAttempt < 1) return 0;
        long factor = 1L << Math.min(failedAttempt - 1, 30);
        long delay = initialDelayMs > Long.MAX_VALUE / Math.max(1, factor)
                ? Long.MAX_VALUE : initialDelayMs * factor;
        return Math.min(delay, maxDelayMs);
    }

    public static ReconnectPolicy desktopDefault() {
        return new ReconnectPolicy(5, 1_000L, 16_000L);
    }
}
