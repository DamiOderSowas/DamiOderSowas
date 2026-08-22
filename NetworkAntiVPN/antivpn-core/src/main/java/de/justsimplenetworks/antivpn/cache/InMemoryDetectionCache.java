package de.justsimplenetworks.antivpn.cache;

import de.justsimplenetworks.antivpn.api.AggregatedDetectionResult;
import de.justsimplenetworks.antivpn.util.IpAddressUtils;

import java.time.Duration;
import java.util.Optional;

/** Default in-memory {@link DetectionCache}, backed by {@link TtlCache}. */
public final class InMemoryDetectionCache implements DetectionCache {

    private final TtlCache<String, AggregatedDetectionResult> delegate;

    public InMemoryDetectionCache(int maxEntries, Duration cleanupInterval) {
        this.delegate = new TtlCache<>(maxEntries, cleanupInterval);
    }

    @Override
    public Optional<AggregatedDetectionResult> get(String ip) {
        return delegate.get(IpAddressUtils.normalize(ip));
    }

    @Override
    public void put(String ip, AggregatedDetectionResult result, Duration ttl) {
        delegate.put(IpAddressUtils.normalize(ip), result, ttl);
    }

    @Override
    public void invalidate(String ip) {
        delegate.invalidate(IpAddressUtils.normalize(ip));
    }

    @Override
    public void invalidateAll() {
        delegate.invalidateAll();
    }

    @Override
    public CacheStats stats() {
        return new CacheStats(delegate.hitCount(), delegate.missCount(), delegate.size(), delegate.maxEntries());
    }

    public void shutdown() {
        delegate.shutdown();
    }
}
