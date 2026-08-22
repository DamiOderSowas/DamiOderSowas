package de.justsimplenetworks.antivpn.providers;

import de.justsimplenetworks.antivpn.api.DetectionResult;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;

/**
 * A test/demo {@link DetectionProvider} that performs no network I/O.
 * <p>
 * By default it reports "no verdict" (every boolean flag left {@code null},
 * low confidence, and an explanatory evidence entry) for every IP - it is
 * deliberately honest about not having real intelligence rather than
 * fabricating a detection. This lets the whole pipeline (cache, aggregation,
 * scoring, decision, commands) be exercised end-to-end without any external
 * API, and lets unit tests inject a custom {@code resolver} to simulate
 * specific verdicts, timeouts, or provider errors.
 * <p>
 * Ships disabled by default in {@code config.yml}; operators must configure
 * at least one real {@link ConfigurableHttpDetectionProvider} (or a future
 * bespoke provider) for production VPN/proxy detection.
 */
public final class MockDetectionProvider implements DetectionProvider {

    private final ProviderConfig config;
    private final ProviderHealthTracker health;
    private final Function<String, CompletableFuture<DetectionResult>> resolver;

    public MockDetectionProvider(ProviderConfig config) {
        this(config, ip -> CompletableFuture.completedFuture(noVerdict(ip, config.name())));
    }

    /** Test constructor: supply a custom async resolver to simulate any provider behaviour. */
    public MockDetectionProvider(ProviderConfig config, Function<String, CompletableFuture<DetectionResult>> resolver) {
        this.config = Objects.requireNonNull(config, "config must not be null");
        this.resolver = Objects.requireNonNull(resolver, "resolver must not be null");
        this.health = new ProviderHealthTracker(config.name());
    }

    private static DetectionResult noVerdict(String ip, String providerName) {
        return DetectionResult.builder(ip, providerName)
                .confidence(0.0)
                .evidence(List.of("mock provider: no real intelligence configured"))
                .latency(Duration.ZERO)
                .build();
    }

    @Override
    public String name() {
        return config.name();
    }

    @Override
    public ProviderConfig config() {
        return config;
    }

    @Override
    public ProviderHealthTracker health() {
        return health;
    }

    @Override
    public CompletableFuture<DetectionResult> detect(String ip) {
        Instant start = Instant.now();
        return resolver.apply(ip).handle((result, throwable) -> {
            Duration latency = Duration.between(start, Instant.now());
            if (throwable != null) {
                health.recordFailure(throwable.getMessage());
                return DetectionResult.error(ip, config.name(), throwable.getMessage());
            }
            if (result.isFailed()) {
                health.recordFailure(result.error());
            } else {
                health.recordSuccess(latency);
            }
            return result;
        });
    }
}
