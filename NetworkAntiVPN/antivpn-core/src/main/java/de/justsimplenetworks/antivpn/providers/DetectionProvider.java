package de.justsimplenetworks.antivpn.providers;

import de.justsimplenetworks.antivpn.api.DetectionResult;

import java.util.concurrent.CompletableFuture;

/**
 * A single, swappable data source for IP intelligence (VPN/proxy/hosting/
 * Tor/residential/country/ASN/reputation). Implementations must:
 * <ul>
 *   <li>never block the calling thread - all I/O happens asynchronously;</li>
 *   <li>never throw - failures are reported via {@link DetectionResult#error()};</li>
 *   <li>honor {@link ProviderConfig#timeout()} themselves or rely on the
 *       aggregator's timeout, whichever is documented by the implementation;</li>
 *   <li>never claim a boolean flag it does not actually have evidence for -
 *       leave it {@code null} instead of guessing {@code false}.</li>
 * </ul>
 */
public interface DetectionProvider {

    /** Unique, stable provider name as used in {@code config.yml} and logs. */
    String name();

    /** Static configuration for this provider. */
    ProviderConfig config();

    /** Whether this provider is currently enabled and should be queried. */
    default boolean isEnabled() {
        return config().enabled();
    }

    /** Current health tracker for this provider instance. */
    ProviderHealthTracker health();

    /**
     * Queries this provider for the given IP address. The returned future
     * always completes normally (never exceptionally) with either a
     * populated {@link DetectionResult} or one carrying {@link DetectionResult#error()}.
     */
    CompletableFuture<DetectionResult> detect(String ip);
}
