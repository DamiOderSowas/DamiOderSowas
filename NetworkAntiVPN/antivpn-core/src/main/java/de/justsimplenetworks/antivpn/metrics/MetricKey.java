package de.justsimplenetworks.antivpn.metrics;

/** Every counter NetworkAntiVPN tracks, exposed via {@code /antivpn status}. */
public enum MetricKey {
    TOTAL_CHECKS,
    CACHE_HITS,
    CACHE_MISSES,
    PROVIDER_REQUESTS,
    PROVIDER_FAILURES,
    VPN_DETECTIONS,
    PROXY_DETECTIONS,
    HOSTING_DETECTIONS,
    DATACENTER_DETECTIONS,
    TOR_DETECTIONS,
    RESIDENTIAL_PROXY_DETECTIONS,
    COUNTRY_BLOCKS,
    WHITELIST_BYPASSES,
    BLACKLIST_BLOCKS,
    TEMPORARY_BYPASSES,
    RATE_LIMITS,
    FALSE_POSITIVES
}
