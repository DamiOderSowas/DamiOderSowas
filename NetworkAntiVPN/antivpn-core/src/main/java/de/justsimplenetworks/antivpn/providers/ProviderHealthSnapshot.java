package de.justsimplenetworks.antivpn.providers;

import java.time.Duration;
import java.time.Instant;

/**
 * Immutable point-in-time view of a provider's health, returned by
 * {@link ProviderHealthTracker#snapshot()} for {@code /antivpn providers} and {@code /antivpn status}.
 */
public record ProviderHealthSnapshot(
        String providerName,
        ProviderHealth status,
        Duration averageLatency,
        long successCount,
        long errorCount,
        long rateLimitedCount,
        Instant lastSuccessAt,
        Instant lastErrorAt,
        String lastErrorMessage
) {
}
