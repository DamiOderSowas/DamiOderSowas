package de.justsimplenetworks.antivpn.providers;

import java.time.Duration;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Static configuration for a single {@link DetectionProvider}, loaded from
 * {@code config.yml} ({@code providers.<name>.*}). Secrets (API keys) are
 * never stored here directly - only the name of the environment variable
 * that holds them, resolved at request time by the provider implementation.
 *
 * @param name                the provider's unique name
 * @param enabled             whether the provider should be queried at all
 * @param priority            lower runs first when providers are queried sequentially due to concurrency limits
 * @param weight              relative weight in aggregation/scoring, {@code > 0}
 * @param timeout             per-request timeout
 * @param rateLimitPerMinute  maximum requests this provider allows per minute, {@code <= 0} means unlimited
 * @param capabilities        signals this provider can report
 * @param urlTemplate         HTTP endpoint template containing an {@code {ip}} placeholder;
 *                            must be {@code null} for non-HTTP providers such as {@link MockDetectionProvider}
 * @param apiKeyEnvVar        name of the environment variable holding the API key, or {@code null}
 * @param apiKeyHeader        HTTP header name to send the key as, or {@code null} to use a query parameter
 * @param apiKeyQueryParam    query parameter name to send the key as, used only if {@link #apiKeyHeader()} is {@code null}
 * @param fieldMapping        maps a dot-notation JSON response path to a {@code DetectionResult} field name
 *                            (one of: vpn, proxy, hosting, datacenter, tor, residentialProxy, residential,
 *                            country, continent, asn, isp, organization, hostname, riskScore)
 */
public record ProviderConfig(
        String name,
        boolean enabled,
        int priority,
        double weight,
        Duration timeout,
        int rateLimitPerMinute,
        Set<ProviderCapability> capabilities,
        String urlTemplate,
        String apiKeyEnvVar,
        String apiKeyHeader,
        String apiKeyQueryParam,
        Map<String, String> fieldMapping
) {
    public ProviderConfig {
        Objects.requireNonNull(name, "name must not be null");
        capabilities = capabilities == null ? Set.of() : Set.copyOf(capabilities);
        fieldMapping = fieldMapping == null ? Map.of() : Map.copyOf(fieldMapping);
        if (weight <= 0) {
            weight = 1.0;
        }
        if (timeout == null || timeout.isNegative() || timeout.isZero()) {
            timeout = Duration.ofSeconds(3);
        }
    }

    /** Resolves the API key from the environment, or {@code null} if not configured / not set. */
    public String resolveApiKey() {
        if (apiKeyEnvVar == null || apiKeyEnvVar.isBlank()) {
            return null;
        }
        return System.getenv(apiKeyEnvVar);
    }
}
