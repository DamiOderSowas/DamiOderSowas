package de.justsimplenetworks.antivpn.metrics;

import java.util.EnumMap;
import java.util.Map;
import java.util.concurrent.atomic.LongAdder;

/**
 * Lightweight in-process counters for {@code /antivpn status}. Deliberately
 * dependency-free (no Micrometer/Prometheus) to keep the core module small;
 * a platform integration can bridge {@link #snapshot()} into a metrics
 * system of its choice (e.g. a future NetworkMetrics).
 */
public final class MetricsRegistry {

    private final Map<MetricKey, LongAdder> counters = new EnumMap<>(MetricKey.class);
    private final LongAdder providerLatencyTotalMillis = new LongAdder();
    private final LongAdder providerLatencySamples = new LongAdder();
    private final boolean enabled;

    public MetricsRegistry(boolean enabled) {
        this.enabled = enabled;
        for (MetricKey key : MetricKey.values()) {
            counters.put(key, new LongAdder());
        }
    }

    public void increment(MetricKey key) {
        increment(key, 1);
    }

    public void increment(MetricKey key, long amount) {
        if (enabled) {
            counters.get(key).add(amount);
        }
    }

    public void recordProviderLatency(long millis) {
        if (enabled) {
            providerLatencyTotalMillis.add(millis);
            providerLatencySamples.increment();
        }
    }

    public long get(MetricKey key) {
        return counters.get(key).sum();
    }

    public double averageProviderLatencyMillis() {
        long samples = providerLatencySamples.sum();
        return samples == 0 ? 0.0 : (double) providerLatencyTotalMillis.sum() / samples;
    }

    public Map<MetricKey, Long> snapshot() {
        Map<MetricKey, Long> result = new EnumMap<>(MetricKey.class);
        for (Map.Entry<MetricKey, LongAdder> entry : counters.entrySet()) {
            result.put(entry.getKey(), entry.getValue().sum());
        }
        return result;
    }
}
