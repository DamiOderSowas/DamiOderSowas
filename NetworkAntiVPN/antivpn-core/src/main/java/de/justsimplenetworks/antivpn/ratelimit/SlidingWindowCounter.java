package de.justsimplenetworks.antivpn.ratelimit;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;

/**
 * Thread-safe sliding-window request counter: allows at most
 * {@code maxEvents} calls to {@link #tryRecord()} within any
 * {@code window}-long trailing interval.
 */
public final class SlidingWindowCounter {

    private final int maxEvents;
    private final Duration window;
    private final Deque<Instant> timestamps = new ArrayDeque<>();
    private volatile Instant lastAccess = Instant.now();

    public SlidingWindowCounter(int maxEvents, Duration window) {
        this.maxEvents = maxEvents;
        this.window = window;
    }

    public synchronized boolean tryRecord() {
        lastAccess = Instant.now();
        evictOld(lastAccess);
        if (timestamps.size() >= maxEvents) {
            return false;
        }
        timestamps.addLast(lastAccess);
        return true;
    }

    public synchronized int currentCount() {
        evictOld(Instant.now());
        return timestamps.size();
    }

    private void evictOld(Instant now) {
        while (!timestamps.isEmpty() && Duration.between(timestamps.peekFirst(), now).compareTo(window) > 0) {
            timestamps.pollFirst();
        }
    }

    /** Whether this counter has been idle long enough to be safely garbage-collected. */
    public boolean isStale(Duration idleThreshold) {
        return Duration.between(lastAccess, Instant.now()).compareTo(idleThreshold) > 0
                && currentCount() == 0;
    }
}
