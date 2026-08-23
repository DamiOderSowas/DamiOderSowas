package de.justsimplenetworks.antivpn.cache;

import de.justsimplenetworks.antivpn.api.DetectionResult;
import de.justsimplenetworks.antivpn.util.IpAddressUtils;

import java.time.Duration;
import java.util.Optional;

/** Default in-memory {@link ProviderResultCache}, backed by {@link TtlCache}. */
public final class InMemoryProviderResultCache implements ProviderResultCache {

    private final TtlCache<String, DetectionResult> delegate;

    public InMemoryProviderResultCache(int maxEntries, Duration cleanupInterval) {
        this.delegate = new TtlCache<>(maxEntries, cleanupInterval);
    }

    private static String key(String providerName, String ip) {
        return providerName + ':' + IpAddressUtils.normalize(ip);
    }

    @Override
    public Optional<DetectionResult> get(String providerName, String ip) {
        return delegate.get(key(providerName, ip));
    }

    @Override
    public void put(String providerName, String ip, DetectionResult result, Duration ttl) {
        delegate.put(key(providerName, ip), result, ttl);
    }

    @Override
    public void invalidate(String providerName, String ip) {
        delegate.invalidate(key(providerName, ip));
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
