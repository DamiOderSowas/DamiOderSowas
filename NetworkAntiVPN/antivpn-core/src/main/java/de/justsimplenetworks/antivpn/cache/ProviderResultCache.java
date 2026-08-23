package de.justsimplenetworks.antivpn.cache;

import de.justsimplenetworks.antivpn.api.DetectionResult;

import java.time.Duration;
import java.util.Optional;

/**
 * Per-provider, per-IP cache. Lets {@code detection.DetectionAggregator} skip
 * re-querying an individual provider whose own cached result is still valid,
 * even if the overall aggregated verdict needs to be recomputed (e.g. after
 * a config reload changes scoring weights).
 */
public interface ProviderResultCache {

    Optional<DetectionResult> get(String providerName, String ip);

    void put(String providerName, String ip, DetectionResult result, Duration ttl);

    void invalidate(String providerName, String ip);

    void invalidateAll();

    CacheStats stats();
}
