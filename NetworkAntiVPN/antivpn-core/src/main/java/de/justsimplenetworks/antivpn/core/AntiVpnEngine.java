package de.justsimplenetworks.antivpn.core;

import de.justsimplenetworks.antivpn.api.*;
import de.justsimplenetworks.antivpn.api.event.*;
import de.justsimplenetworks.antivpn.blacklist.BlacklistEntry;
import de.justsimplenetworks.antivpn.blacklist.BlacklistService;
import de.justsimplenetworks.antivpn.bypass.BypassService;
import de.justsimplenetworks.antivpn.cache.DetectionCache;
import de.justsimplenetworks.antivpn.config.AntiVpnConfig;
import de.justsimplenetworks.antivpn.country.CountryFilterResult;
import de.justsimplenetworks.antivpn.country.CountryFilterService;
import de.justsimplenetworks.antivpn.detection.DetectionAggregator;
import de.justsimplenetworks.antivpn.decision.DecisionEngine;
import de.justsimplenetworks.antivpn.integration.networkcore.NetworkCoreBridge;
import de.justsimplenetworks.antivpn.logging.SafeLogger;
import de.justsimplenetworks.antivpn.metrics.MetricKey;
import de.justsimplenetworks.antivpn.metrics.MetricsRegistry;
import de.justsimplenetworks.antivpn.ratelimit.RateLimiterService;
import de.justsimplenetworks.antivpn.scoring.RiskScoringEngine;
import de.justsimplenetworks.antivpn.scoring.ScoringContext;
import de.justsimplenetworks.antivpn.security.IpHasher;
import de.justsimplenetworks.antivpn.whitelist.WhitelistEntry;
import de.justsimplenetworks.antivpn.whitelist.WhitelistService;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * The central orchestrator implementing {@link AntiVpnService}. Every
 * component is constructor-injected so this class contains no business
 * logic of its own beyond sequencing the pipeline described in
 * {@code docs/ARCHITECTURE.md}:
 * <pre>
 * rate limit -&gt; bypass -&gt; whitelist -&gt; blacklist (ip/cidr/player)
 *   -&gt; cache/detection -&gt; blacklist (asn) -&gt; country filter
 *   -&gt; risk scoring -&gt; decision engine
 * </pre>
 * Whitelist, bypass, and blacklist can each short-circuit the entire
 * pipeline before a single provider is ever queried, matching the
 * specification's priority rules.
 */
public final class AntiVpnEngine implements AntiVpnService {

    private static final SafeLogger LOGGER = SafeLogger.of(AntiVpnEngine.class);
    private static final Duration SESSION_RETENTION = Duration.ofHours(2);

    private final DetectionAggregator aggregator;
    private final DetectionCache detectionCache;
    private final RiskScoringEngine scoringEngine;
    private final DecisionEngine decisionEngine;
    private final WhitelistService whitelistService;
    private final BlacklistService blacklistService;
    private final BypassService bypassService;
    private final CountryFilterService countryFilterService;
    private final RateLimiterService rateLimiterService;
    private final MetricsRegistry metrics;
    private final EventBus eventBus;
    private final NetworkCoreBridge networkCoreBridge;
    private final IpHasher ipHasher;
    private final AntiVpnConfig config;
    private final boolean cacheEnabled;
    private final boolean storeRawIp;
    private final Duration cacheTtl;
    private final Duration overallTimeout;

    private final Map<UUID, TimestampedDecision> sessionResults = new ConcurrentHashMap<>();
    private final Map<UUID, PlayerRiskProfile> riskProfiles = new ConcurrentHashMap<>();
    private final ScheduledExecutorService sessionCleaner;

    public AntiVpnEngine(DetectionAggregator aggregator,
                          DetectionCache detectionCache,
                          RiskScoringEngine scoringEngine,
                          DecisionEngine decisionEngine,
                          WhitelistService whitelistService,
                          BlacklistService blacklistService,
                          BypassService bypassService,
                          CountryFilterService countryFilterService,
                          RateLimiterService rateLimiterService,
                          MetricsRegistry metrics,
                          EventBus eventBus,
                          NetworkCoreBridge networkCoreBridge,
                          IpHasher ipHasher,
                          AntiVpnConfig config,
                          boolean cacheEnabled,
                          boolean storeRawIp,
                          Duration cacheTtl,
                          Duration overallTimeout) {
        this.aggregator = Objects.requireNonNull(aggregator);
        this.detectionCache = Objects.requireNonNull(detectionCache);
        this.scoringEngine = Objects.requireNonNull(scoringEngine);
        this.decisionEngine = Objects.requireNonNull(decisionEngine);
        this.whitelistService = Objects.requireNonNull(whitelistService);
        this.blacklistService = Objects.requireNonNull(blacklistService);
        this.bypassService = Objects.requireNonNull(bypassService);
        this.countryFilterService = Objects.requireNonNull(countryFilterService);
        this.rateLimiterService = Objects.requireNonNull(rateLimiterService);
        this.metrics = Objects.requireNonNull(metrics);
        this.eventBus = Objects.requireNonNull(eventBus);
        this.networkCoreBridge = Objects.requireNonNull(networkCoreBridge);
        this.ipHasher = Objects.requireNonNull(ipHasher);
        this.config = Objects.requireNonNull(config);
        this.cacheEnabled = cacheEnabled;
        this.storeRawIp = storeRawIp;
        this.cacheTtl = Objects.requireNonNull(cacheTtl);
        this.overallTimeout = Objects.requireNonNull(overallTimeout);
        this.sessionCleaner = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "antivpn-session-cleanup");
            t.setDaemon(true);
            return t;
        });
        this.sessionCleaner.scheduleAtFixedRate(this::evictStaleSessions, 10, 10, TimeUnit.MINUTES);
    }

    @Override
    public CompletableFuture<DecisionResult> analyzeConnection(ConnectionContext context) {
        Objects.requireNonNull(context, "context must not be null");
        metrics.increment(MetricKey.TOTAL_CHECKS);
        eventBus.publish(new ConnectionAnalysisStartedEvent(context));

        Optional<DecisionResult> shortCircuit = evaluateShortCircuits(context);
        if (shortCircuit.isPresent()) {
            return CompletableFuture.completedFuture(finish(context, shortCircuit.get()));
        }

        CompletableFuture<AggregatedDetectionResult> aggregatedFuture = resolveAggregatedResult(context.ip());

        return aggregatedFuture
                .orTimeout(overallTimeout.toMillis(), TimeUnit.MILLISECONDS)
                .exceptionally(throwable -> AggregatedDetectionResult.unknown(context.ip(), java.util.List.of()))
                .thenApply(aggregated -> {
                    eventBus.publish(new ConnectionAnalysisCompletedEvent(context, aggregated));
                    recordDetectionMetrics(context, aggregated);
                    return finishDetectionPipeline(context, aggregated);
                });
    }

    private Optional<DecisionResult> evaluateShortCircuits(ConnectionContext context) {
        if (!rateLimiterService.tryAcquireForIp(context.ip())
                || !rateLimiterService.tryAcquireForPlayer(context.playerUuid())) {
            metrics.increment(MetricKey.RATE_LIMITS);
            return Optional.of(decisionResult(Decision.RATE_LIMITED, DecisionReason.RATE_LIMITED, 0, RiskLevel.SAFE, null));
        }

        if (context.playerUuid() != null && bypassService.isActive(context.playerUuid())) {
            return Optional.of(decisionResult(Decision.BYPASS, DecisionReason.TEMPORARY_BYPASS, 0, RiskLevel.SAFE, null));
        }

        Optional<WhitelistEntry> whitelisted = whitelistService.check(context.ip(), context.playerUuid(), context.playerName());
        if (whitelisted.isPresent()) {
            metrics.increment(MetricKey.WHITELIST_BYPASSES);
            return Optional.of(decisionResult(Decision.BYPASS, whitelistReason(whitelisted.get()), 0, RiskLevel.SAFE, null));
        }

        Optional<BlacklistEntry> blacklisted = blacklistService.check(context.ip(), context.playerUuid(), context.playerName());
        if (blacklisted.isPresent()) {
            metrics.increment(MetricKey.BLACKLIST_BLOCKS);
            BlacklistEntry entry = blacklisted.get();
            Decision decision = entry.isTemporary() ? Decision.TEMPORARY_BLOCK : Decision.BLOCK;
            return Optional.of(decisionResultWithExpiry(decision, blacklistReason(entry), 100, RiskLevel.CRITICAL, null, entry.expiresAt()));
        }

        return Optional.empty();
    }

    private CompletableFuture<AggregatedDetectionResult> resolveAggregatedResult(String ip) {
        if (cacheEnabled) {
            Optional<AggregatedDetectionResult> cached = detectionCache.get(ip);
            if (cached.isPresent()) {
                metrics.increment(MetricKey.CACHE_HITS);
                return CompletableFuture.completedFuture(cached.get());
            }
            metrics.increment(MetricKey.CACHE_MISSES);
        }
        return aggregator.aggregate(ip).thenApply(result -> {
            if (cacheEnabled && result.successfulProviderCount() > 0) {
                detectionCache.put(ip, result, cacheTtl);
            }
            return result;
        });
    }

    private void recordDetectionMetrics(ConnectionContext context, AggregatedDetectionResult aggregated) {
        if (!aggregated.fromCache()) {
            metrics.increment(MetricKey.PROVIDER_REQUESTS, aggregated.providerResults().size());
            metrics.increment(MetricKey.PROVIDER_FAILURES, aggregated.failedProviderCount());
            for (DetectionResult r : aggregated.providerResults()) {
                if (!r.isFailed() && !r.cached()) {
                    metrics.recordProviderLatency(r.latency().toMillis());
                }
            }
        }
        if (aggregated.vpn()) {
            metrics.increment(MetricKey.VPN_DETECTIONS);
            eventBus.publish(new VPNDetectedEvent(context, aggregated));
        }
        if (aggregated.proxy()) {
            metrics.increment(MetricKey.PROXY_DETECTIONS);
            eventBus.publish(new ProxyDetectedEvent(context, aggregated));
        }
        if (aggregated.hosting() || aggregated.datacenter()) {
            metrics.increment(MetricKey.HOSTING_DETECTIONS);
            if (aggregated.datacenter()) {
                metrics.increment(MetricKey.DATACENTER_DETECTIONS);
            }
            eventBus.publish(new HostingDetectedEvent(context, aggregated));
        }
        if (aggregated.tor()) {
            metrics.increment(MetricKey.TOR_DETECTIONS);
            eventBus.publish(new TorDetectedEvent(context, aggregated));
        }
        if (aggregated.residentialProxy()) {
            metrics.increment(MetricKey.RESIDENTIAL_PROXY_DETECTIONS);
        }
    }

    private DecisionResult finishDetectionPipeline(ConnectionContext context, AggregatedDetectionResult aggregated) {
        Optional<BlacklistEntry> asnBlacklisted = blacklistService.checkAsn(aggregated.asn());
        if (asnBlacklisted.isPresent()) {
            metrics.increment(MetricKey.BLACKLIST_BLOCKS);
            BlacklistEntry entry = asnBlacklisted.get();
            Decision decision = entry.isTemporary() ? Decision.TEMPORARY_BLOCK : Decision.BLOCK;
            return finish(context, decisionResultWithExpiry(decision, DecisionReason.BLACKLIST_ASN, 100,
                    RiskLevel.CRITICAL, aggregated, entry.expiresAt()));
        }

        CountryFilterResult countryResult = countryFilterService.evaluate(
                aggregated.country(), aggregated.continent(), context.serverName(), context.serverGroup());
        if (!countryResult.allowed()) {
            metrics.increment(MetricKey.COUNTRY_BLOCKS);
            return finish(context, decisionResult(Decision.BLOCK, countryResult.reason(), 100, RiskLevel.CRITICAL, aggregated));
        }

        PlayerRiskProfile previous = context.playerUuid() != null ? riskProfiles.get(context.playerUuid()) : null;
        ScoringContext scoringContext = new ScoringContext(
                previous != null ? previous.riskScore() : null, countryResult.highRisk());
        int riskScore = scoringEngine.score(aggregated, scoringContext);
        RiskLevel riskLevel = scoringEngine.level(riskScore);

        if (riskLevel == RiskLevel.HIGH || riskLevel == RiskLevel.CRITICAL) {
            eventBus.publish(new HighRiskConnectionEvent(context, aggregated, riskScore, riskLevel));
        }

        DecisionResult decision = decisionEngine.evaluate(aggregated, riskScore, riskLevel);
        return finish(context, decision);
    }

    private DecisionResult finish(ConnectionContext context, DecisionResult decision) {
        eventBus.publish(new ConnectionDecisionEvent(context, decision));
        if (decision.isBlocked()) {
            eventBus.publish(new ConnectionBlockedEvent(context, decision));
        } else {
            eventBus.publish(new ConnectionAllowedEvent(context, decision));
        }

        if (context.playerUuid() != null) {
            storeSessionResult(context.playerUuid(), decision);
            recordRiskProfile(context, decision);
        }
        return decision;
    }

    private void recordRiskProfile(ConnectionContext context, DecisionResult decision) {
        UUID uuid = context.playerUuid();
        String ipHash = ipHasher.hash(context.ip());
        PlayerRiskProfile profile = new PlayerRiskProfile(
                uuid, context.playerName(), storeRawIp ? context.ip() : null, ipHash,
                decision.aggregatedResult() != null ? decision.aggregatedResult().country() : null,
                decision.aggregatedResult() != null ? decision.aggregatedResult().asn() : null,
                decision.riskScore(), context.platform(), Instant.now());
        riskProfiles.put(uuid, profile);
        networkCoreBridge.publishRiskProfile(profile);
    }

    private DecisionReason whitelistReason(WhitelistEntry entry) {
        if (entry.isTemporary()) {
            return DecisionReason.TEMPORARY_WHITELIST;
        }
        return switch (entry.type()) {
            case IP -> DecisionReason.WHITELIST_IP;
            case CIDR -> DecisionReason.WHITELIST_CIDR;
            case PLAYER -> DecisionReason.WHITELIST_PLAYER;
        };
    }

    private DecisionReason blacklistReason(BlacklistEntry entry) {
        if (entry.isTemporary()) {
            return DecisionReason.TEMPORARY_BLACKLIST;
        }
        return switch (entry.type()) {
            case IP -> DecisionReason.BLACKLIST_IP;
            case CIDR -> DecisionReason.BLACKLIST_CIDR;
            case PLAYER -> DecisionReason.BLACKLIST_PLAYER;
            case ASN -> DecisionReason.BLACKLIST_ASN;
        };
    }

    private DecisionResult decisionResult(Decision decision, DecisionReason reason, int riskScore,
                                           RiskLevel riskLevel, AggregatedDetectionResult aggregated) {
        return decisionResultWithExpiry(decision, reason, riskScore, riskLevel, aggregated, null);
    }

    private DecisionResult decisionResultWithExpiry(Decision decision, DecisionReason reason, int riskScore,
                                                      RiskLevel riskLevel, AggregatedDetectionResult aggregated,
                                                      Instant blockedUntil) {
        String key = de.justsimplenetworks.antivpn.decision.DecisionMessageKeys.resolve(decision, reason);
        String messageKey = key == null ? "allowed-silent" : key;
        return new DecisionResult(decision, reason, riskScore, riskLevel, aggregated, messageKey, blockedUntil, Instant.now());
    }

    @Override
    public Optional<DecisionResult> getSessionResult(UUID playerUuid) {
        TimestampedDecision entry = sessionResults.get(playerUuid);
        return entry == null ? Optional.empty() : Optional.of(entry.decision());
    }

    @Override
    public void storeSessionResult(UUID playerUuid, DecisionResult result) {
        sessionResults.put(playerUuid, new TimestampedDecision(result, Instant.now()));
    }

    @Override
    public void clearSessionResult(UUID playerUuid) {
        sessionResults.remove(playerUuid);
    }

    @Override
    public Optional<PlayerRiskProfile> getRiskProfile(UUID playerUuid) {
        return Optional.ofNullable(riskProfiles.get(playerUuid));
    }

    @Override
    public EventBus events() {
        return eventBus;
    }

    @Override
    public void reload() {
        whitelistService.reloadConfigEntries(config.whitelistIps(), config.whitelistCidrs(), config.whitelistPlayers());
        blacklistService.reloadConfigEntries(config.blacklistIps(), config.blacklistCidrs(),
                config.blacklistPlayers(), config.blacklistAsns());
        countryFilterService.reload(config.globalCountryRule(), config.serverCountryRules(), config.serverGroupCountryRules());
        scoringEngine.reload(config.riskWeights());
        decisionEngine.reload(config.decisionRuleSet(), config.fallbackPolicy());
        LOGGER.info("AntiVpnEngine configuration reloaded (whitelist, blacklist, countries, risk weights, decision rules)");
    }

    @Override
    public void shutdown() {
        sessionCleaner.shutdown();
        whitelistService.shutdown();
        blacklistService.shutdown();
        bypassService.shutdown();
        rateLimiterService.shutdown();
        if (detectionCache instanceof de.justsimplenetworks.antivpn.cache.InMemoryDetectionCache inMemory) {
            inMemory.shutdown();
        }
    }

    private void evictStaleSessions() {
        Instant threshold = Instant.now().minus(SESSION_RETENTION);
        sessionResults.entrySet().removeIf(e -> e.getValue().storedAt().isBefore(threshold));
    }

    private record TimestampedDecision(DecisionResult decision, Instant storedAt) {
    }
}
