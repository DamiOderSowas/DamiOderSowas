package de.justsimplenetworks.antivpn.detection;

import de.justsimplenetworks.antivpn.api.AggregatedDetectionResult;
import de.justsimplenetworks.antivpn.api.DetectionResult;
import de.justsimplenetworks.antivpn.cache.ProviderResultCache;
import de.justsimplenetworks.antivpn.providers.DetectionProvider;
import de.justsimplenetworks.antivpn.ratelimit.RateLimiterService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Function;

/**
 * Queries every enabled {@link DetectionProvider} for an IP in parallel and
 * merges the individual {@link DetectionResult}s into one
 * {@link AggregatedDetectionResult} using confidence/weight-aware majority
 * voting. A single slow or offline provider can never stall this pipeline:
 * every provider call is time-boxed independently, and a per-provider
 * timeout/error simply excludes that provider from the vote instead of
 * failing the whole aggregation (see {@code FALLBACK} policy in
 * {@code decision.DecisionEngine} for what happens when every provider fails).
 * <p>
 * Provider I/O is executed on a bounded {@link ExecutorService} (constructor
 * injected) so the total number of in-flight provider requests is capped
 * regardless of how many players connect at once - this keeps the process
 * responsive and respects each provider's own rate limits.
 */
public final class DetectionAggregator {

    private static final Logger LOGGER = LoggerFactory.getLogger(DetectionAggregator.class);

    private final List<DetectionProvider> providers;
    private final ProviderResultCache providerResultCache;
    private final ExecutorService providerExecutor;
    private final RateLimiterService rateLimiterService;
    private final Duration providerCacheTtl;
    private final Duration perProviderTimeout;
    private final double agreementThreshold;

    public DetectionAggregator(List<DetectionProvider> providers,
                                ProviderResultCache providerResultCache,
                                ExecutorService providerExecutor,
                                RateLimiterService rateLimiterService,
                                Duration providerCacheTtl,
                                Duration perProviderTimeout,
                                double agreementThreshold) {
        this.providers = List.copyOf(providers);
        this.providerResultCache = Objects.requireNonNull(providerResultCache, "providerResultCache must not be null");
        this.providerExecutor = Objects.requireNonNull(providerExecutor, "providerExecutor must not be null");
        this.rateLimiterService = Objects.requireNonNull(rateLimiterService, "rateLimiterService must not be null");
        this.providerCacheTtl = Objects.requireNonNull(providerCacheTtl, "providerCacheTtl must not be null");
        this.perProviderTimeout = Objects.requireNonNull(perProviderTimeout, "perProviderTimeout must not be null");
        this.agreementThreshold = agreementThreshold;
    }

    public CompletableFuture<AggregatedDetectionResult> aggregate(String ip) {
        List<DetectionProvider> enabled = providers.stream().filter(DetectionProvider::isEnabled).toList();
        if (enabled.isEmpty()) {
            return CompletableFuture.completedFuture(AggregatedDetectionResult.unknown(ip, List.of()));
        }

        List<CompletableFuture<DetectionResult>> futures = new ArrayList<>(enabled.size());
        for (DetectionProvider provider : enabled) {
            futures.add(queryOne(provider, ip));
        }

        return CompletableFuture.allOf(futures.toArray(CompletableFuture[]::new))
                .handle((unused, throwable) -> futures.stream().map(this::joinSafely).toList())
                .thenApply(results -> merge(ip, results));
    }

    private DetectionResult joinSafely(CompletableFuture<DetectionResult> future) {
        try {
            return future.join();
        } catch (RuntimeException e) {
            return DetectionResult.error("unknown", "unknown", "internal aggregation failure: " + e.getMessage());
        }
    }

    private CompletableFuture<DetectionResult> queryOne(DetectionProvider provider, String ip) {
        var cached = providerResultCache.get(provider.name(), ip);
        if (cached.isPresent()) {
            return CompletableFuture.completedFuture(cached.get().withCached(true));
        }

        if (!rateLimiterService.tryAcquireForProvider(provider.name(), provider.config().rateLimitPerMinute())) {
            provider.health().recordRateLimited(Duration.ofSeconds(30));
            return CompletableFuture.completedFuture(
                    DetectionResult.error(ip, provider.name(), "provider rate limit exceeded, request skipped"));
        }

        CompletableFuture<DetectionResult> future = CompletableFuture
                .supplyAsync(() -> provider.detect(ip).join(), providerExecutor)
                .orTimeout(perProviderTimeout.toMillis(), TimeUnit.MILLISECONDS)
                .exceptionally(throwable -> {
                    String message = throwable instanceof TimeoutException
                            ? "provider timed out after " + perProviderTimeout.toMillis() + "ms"
                            : "provider failed: " + throwable.getMessage();
                    provider.health().recordFailure(message);
                    LOGGER.debug("Provider {} did not answer in time for an IP check", provider.name());
                    return DetectionResult.error(ip, provider.name(), message);
                });

        return future.thenApply(result -> {
            if (!result.isFailed()) {
                providerResultCache.put(provider.name(), ip, result, providerCacheTtl);
            }
            return result;
        });
    }

    private AggregatedDetectionResult merge(String ip, List<DetectionResult> results) {
        Map<String, DetectionProvider> byName = new LinkedHashMap<>();
        for (DetectionProvider provider : providers) {
            byName.put(provider.name(), provider);
        }

        List<DetectionResult> successful = results.stream().filter(r -> !r.isFailed()).toList();
        if (successful.isEmpty()) {
            return AggregatedDetectionResult.unknown(ip, results);
        }

        boolean vpn = weightedVote(successful, byName, DetectionResult::vpn);
        boolean proxy = weightedVote(successful, byName, DetectionResult::proxy);
        boolean hosting = weightedVote(successful, byName, DetectionResult::hosting);
        boolean datacenter = weightedVote(successful, byName, DetectionResult::datacenter);
        boolean tor = weightedVote(successful, byName, DetectionResult::tor);
        boolean residentialProxy = weightedVote(successful, byName, DetectionResult::residentialProxy);
        boolean residential = weightedVote(successful, byName, DetectionResult::residential);

        String country = resolveString(successful, byName, DetectionResult::country);
        String continent = resolveString(successful, byName, DetectionResult::continent);
        String isp = resolveString(successful, byName, DetectionResult::isp);
        String organization = resolveString(successful, byName, DetectionResult::organization);
        Integer asn = resolveInt(successful, byName, DetectionResult::asn);

        double confidence = successful.stream()
                .mapToDouble(r -> r.confidence() * weightOf(byName, r.provider()))
                .sum() / totalWeight(successful, byName);

        boolean majoritySuspicious = vpn || proxy || hosting || datacenter || tor || residentialProxy;
        long agreeing = successful.stream().filter(r -> r.isSuspicious() == majoritySuspicious).count();
        double providerAgreement = (double) agreeing / successful.size();

        List<String> evidence = new ArrayList<>();
        for (DetectionResult r : results) {
            if (r.isFailed()) {
                evidence.add(r.provider() + ": error - " + r.error());
            } else {
                evidence.addAll(r.evidence());
            }
        }

        return new AggregatedDetectionResult(ip, Instant.now(), results, vpn, proxy, hosting, datacenter,
                tor, residentialProxy, residential, country, continent, asn, isp, organization,
                confidence, providerAgreement, evidence, false);
    }

    private boolean weightedVote(List<DetectionResult> results, Map<String, DetectionProvider> byName,
                                  Function<DetectionResult, Boolean> extractor) {
        double trueWeight = 0.0;
        double knownWeight = 0.0;
        for (DetectionResult r : results) {
            Boolean value = extractor.apply(r);
            if (value == null) {
                continue;
            }
            double weight = weightOf(byName, r.provider()) * r.confidence();
            knownWeight += weight;
            if (value) {
                trueWeight += weight;
            }
        }
        if (knownWeight <= 0) {
            return false;
        }
        return (trueWeight / knownWeight) >= agreementThreshold;
    }

    private String resolveString(List<DetectionResult> results, Map<String, DetectionProvider> byName,
                                  Function<DetectionResult, String> extractor) {
        Map<String, Double> votes = new LinkedHashMap<>();
        for (DetectionResult r : results) {
            String value = extractor.apply(r);
            if (value == null || value.isBlank()) {
                continue;
            }
            votes.merge(value, weightOf(byName, r.provider()) * r.confidence(), Double::sum);
        }
        return votes.entrySet().stream()
                .max(Map.Entry.comparingByValue())
                .map(Map.Entry::getKey)
                .orElse(null);
    }

    private Integer resolveInt(List<DetectionResult> results, Map<String, DetectionProvider> byName,
                                Function<DetectionResult, Integer> extractor) {
        Map<Integer, Double> votes = new LinkedHashMap<>();
        for (DetectionResult r : results) {
            Integer value = extractor.apply(r);
            if (value == null) {
                continue;
            }
            votes.merge(value, weightOf(byName, r.provider()) * r.confidence(), Double::sum);
        }
        return votes.entrySet().stream()
                .max(Map.Entry.comparingByValue())
                .map(Map.Entry::getKey)
                .orElse(null);
    }

    private double weightOf(Map<String, DetectionProvider> byName, String providerName) {
        DetectionProvider provider = byName.get(providerName);
        return provider == null ? 1.0 : provider.config().weight();
    }

    private double totalWeight(List<DetectionResult> results, Map<String, DetectionProvider> byName) {
        double sum = results.stream().mapToDouble(r -> weightOf(byName, r.provider())).sum();
        return sum <= 0 ? 1.0 : sum;
    }
}
