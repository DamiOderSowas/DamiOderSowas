package de.justsimplenetworks.antivpn.cache;

import de.justsimplenetworks.antivpn.api.AggregatedDetectionResult;

import java.time.Duration;
import java.util.Optional;

/**
 * IP-keyed cache for {@link AggregatedDetectionResult}s. Consulted first on
 * every connection so that a valid cached verdict never triggers a new
 * provider request. The default implementation is in-memory
 * ({@link InMemoryDetectionCache}); a Redis-backed implementation can be
 * plugged in later once NetworkRedis exists, without touching any calling code.
 */
public interface DetectionCache {

    Optional<AggregatedDetectionResult> get(String ip);

    void put(String ip, AggregatedDetectionResult result, Duration ttl);

    void invalidate(String ip);

    void invalidateAll();

    CacheStats stats();
}
