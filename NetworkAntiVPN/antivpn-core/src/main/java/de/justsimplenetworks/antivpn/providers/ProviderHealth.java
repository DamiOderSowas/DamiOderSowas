package de.justsimplenetworks.antivpn.providers;

/** Current operational status of a {@link DetectionProvider}, tracked by {@link ProviderHealthTracker}. */
public enum ProviderHealth {
    /** Requests are succeeding within the configured timeout. */
    ONLINE,
    /** Requests are succeeding but with an elevated error/timeout rate. */
    DEGRADED,
    /** The provider's own rate limit was hit; requests are being deferred. */
    RATE_LIMITED,
    /** The provider is failing every request (timeouts, connection refused, 5xx). */
    OFFLINE
}
