package de.justsimplenetworks.antivpn.providers;

import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Thread-safe health tracker for a single {@link DetectionProvider}. Keeps a
 * small sliding window of recent outcomes so a handful of old failures don't
 * permanently mark a provider {@link ProviderHealth#OFFLINE}.
 */
public final class ProviderHealthTracker {

    private static final int WINDOW_SIZE = 20;
    private static final double DEGRADED_ERROR_RATE = 0.3;
    private static final int CONSECUTIVE_ERRORS_FOR_OFFLINE = 5;

    private final String providerName;
    private final boolean[] window = new boolean[WINDOW_SIZE];
    private int windowIndex;
    private int windowFilled;
    private int consecutiveErrors;

    private final AtomicLong successCount = new AtomicLong();
    private final AtomicLong errorCount = new AtomicLong();
    private final AtomicLong rateLimitedCount = new AtomicLong();
    private final AtomicLong totalLatencyMillis = new AtomicLong();
    private volatile Instant lastSuccessAt;
    private volatile Instant lastErrorAt;
    private volatile String lastErrorMessage;
    private volatile Instant rateLimitedUntil;

    public ProviderHealthTracker(String providerName) {
        this.providerName = providerName;
    }

    public synchronized void recordSuccess(Duration latency) {
        recordOutcome(true);
        successCount.incrementAndGet();
        totalLatencyMillis.addAndGet(latency.toMillis());
        lastSuccessAt = Instant.now();
        consecutiveErrors = 0;
    }

    public synchronized void recordFailure(String errorMessage) {
        recordOutcome(false);
        errorCount.incrementAndGet();
        lastErrorAt = Instant.now();
        lastErrorMessage = errorMessage;
        consecutiveErrors++;
    }

    public void recordRateLimited(Duration cooldown) {
        rateLimitedCount.incrementAndGet();
        rateLimitedUntil = Instant.now().plus(cooldown);
    }

    private void recordOutcome(boolean success) {
        window[windowIndex] = success;
        windowIndex = (windowIndex + 1) % WINDOW_SIZE;
        if (windowFilled < WINDOW_SIZE) {
            windowFilled++;
        }
    }

    public synchronized ProviderHealth status() {
        if (rateLimitedUntil != null && Instant.now().isBefore(rateLimitedUntil)) {
            return ProviderHealth.RATE_LIMITED;
        }
        if (windowFilled == 0) {
            return ProviderHealth.ONLINE;
        }
        if (consecutiveErrors >= CONSECUTIVE_ERRORS_FOR_OFFLINE) {
            return ProviderHealth.OFFLINE;
        }
        int failures = 0;
        for (int i = 0; i < windowFilled; i++) {
            if (!window[i]) {
                failures++;
            }
        }
        double errorRate = (double) failures / windowFilled;
        if (errorRate >= DEGRADED_ERROR_RATE) {
            return ProviderHealth.DEGRADED;
        }
        return ProviderHealth.ONLINE;
    }

    public synchronized ProviderHealthSnapshot snapshot() {
        long successes = successCount.get();
        Duration avgLatency = successes == 0
                ? Duration.ZERO
                : Duration.ofMillis(totalLatencyMillis.get() / successes);
        return new ProviderHealthSnapshot(providerName, status(), avgLatency, successes,
                errorCount.get(), rateLimitedCount.get(), lastSuccessAt, lastErrorAt, lastErrorMessage);
    }
}
