package de.justsimplenetworks.antivpn.core;

import de.justsimplenetworks.antivpn.api.*;
import de.justsimplenetworks.antivpn.api.event.SimpleEventBus;
import de.justsimplenetworks.antivpn.blacklist.BlacklistService;
import de.justsimplenetworks.antivpn.bypass.BypassService;
import de.justsimplenetworks.antivpn.cache.InMemoryDetectionCache;
import de.justsimplenetworks.antivpn.cache.InMemoryProviderResultCache;
import de.justsimplenetworks.antivpn.config.AntiVpnConfig;
import de.justsimplenetworks.antivpn.config.YamlConfig;
import de.justsimplenetworks.antivpn.country.CountryFilterService;
import de.justsimplenetworks.antivpn.country.CountryRule;
import de.justsimplenetworks.antivpn.decision.DecisionEngine;
import de.justsimplenetworks.antivpn.decision.DecisionRuleSet;
import de.justsimplenetworks.antivpn.decision.FallbackPolicy;
import de.justsimplenetworks.antivpn.detection.DetectionAggregator;
import de.justsimplenetworks.antivpn.integration.networkcore.NoopNetworkCoreBridge;
import de.justsimplenetworks.antivpn.providers.DetectionProvider;
import de.justsimplenetworks.antivpn.providers.MockDetectionProvider;
import de.justsimplenetworks.antivpn.providers.ProviderCapability;
import de.justsimplenetworks.antivpn.providers.ProviderConfig;
import de.justsimplenetworks.antivpn.ratelimit.RateLimiterService;
import de.justsimplenetworks.antivpn.scoring.RiskScoringEngine;
import de.justsimplenetworks.antivpn.scoring.RiskWeights;
import de.justsimplenetworks.antivpn.security.IpHasher;
import de.justsimplenetworks.antivpn.whitelist.WhitelistService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayInputStream;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static org.junit.jupiter.api.Assertions.*;

class AntiVpnEngineTest {

    @TempDir
    Path tempDir;

    private ExecutorService providerExecutor;
    private AntiVpnEngine engine;
    private WhitelistService whitelistService;
    private BlacklistService blacklistService;
    private BypassService bypassService;
    private RateLimiterService rateLimiterService;
    private InMemoryDetectionCache detectionCache;

    @AfterEach
    void tearDown() {
        if (engine != null) engine.shutdown();
        if (providerExecutor != null) providerExecutor.shutdownNow();
    }

    private AntiVpnEngine buildEngine(DetectionProvider provider, CountryRule countryRule, int maxConnectionsPerIp) {
        providerExecutor = Executors.newFixedThreadPool(2);
        rateLimiterService = new RateLimiterService(true, maxConnectionsPerIp, Duration.ofSeconds(10), 0, Duration.ofSeconds(10));
        InMemoryProviderResultCache providerResultCache = new InMemoryProviderResultCache(100, Duration.ofSeconds(30));
        DetectionAggregator aggregator = new DetectionAggregator(List.of(provider), providerResultCache,
                providerExecutor, rateLimiterService, Duration.ofSeconds(30), Duration.ofSeconds(2), 0.5);
        detectionCache = new InMemoryDetectionCache(100, Duration.ofSeconds(30));
        RiskScoringEngine scoringEngine = new RiskScoringEngine(RiskWeights.defaults());
        DecisionEngine decisionEngine = new DecisionEngine(DecisionRuleSet.defaults(), FallbackPolicy.FAIL_OPEN);
        whitelistService = new WhitelistService(tempDir.resolve("whitelist-" + UUID.randomUUID() + ".json"));
        blacklistService = new BlacklistService(tempDir.resolve("blacklist-" + UUID.randomUUID() + ".json"));
        bypassService = new BypassService(tempDir.resolve("bypass-" + UUID.randomUUID() + ".json"));
        CountryFilterService countryFilterService = new CountryFilterService(countryRule, java.util.Map.of(), java.util.Map.of());
        AntiVpnConfig config = new AntiVpnConfig(YamlConfig.load(new ByteArrayInputStream(new byte[0])));

        engine = new AntiVpnEngine(aggregator, detectionCache, scoringEngine, decisionEngine, whitelistService,
                blacklistService, bypassService, countryFilterService, rateLimiterService,
                new de.justsimplenetworks.antivpn.metrics.MetricsRegistry(true), new SimpleEventBus(),
                new NoopNetworkCoreBridge(), new IpHasher("test-salt"), config, true, false,
                Duration.ofSeconds(30), Duration.ofSeconds(5));
        return engine;
    }

    private ProviderConfig providerConfig() {
        return new ProviderConfig("mock", true, 1, 1.0, Duration.ofSeconds(2), 0,
                Set.of(ProviderCapability.VPN, ProviderCapability.COUNTRY), null, null, null, null, null);
    }

    private DetectionProvider providerReturning(boolean vpn, String country) {
        return new MockDetectionProvider(providerConfig(), ip -> CompletableFuture.completedFuture(
                DetectionResult.builder(ip, "mock").vpn(vpn).country(country).confidence(1.0).build()));
    }

    private ConnectionContext context(UUID uuid, String name, String ip) {
        return new ConnectionContext(uuid, name, ip, Platform.JAVA, "lobby", "proxy",
                ConnectionContext.Stage.PROXY_LOGIN, Instant.now());
    }

    @Test
    void vpnDetectionBlocksConnection() {
        buildEngine(providerReturning(true, "DE"), CountryRule.disabled(), 100);
        DecisionResult decision = engine.analyzeConnection(context(UUID.randomUUID(), "Player", "1.2.3.4")).join();
        assertEquals(Decision.BLOCK, decision.decision());
        assertEquals(DecisionReason.VPN_DETECTED, decision.reason());
    }

    @Test
    void whitelistOverridesVpnDetection() {
        buildEngine(providerReturning(true, "DE"), CountryRule.disabled(), 100);
        whitelistService.addIp("1.2.3.4", null, "trusted", "staff");
        DecisionResult decision = engine.analyzeConnection(context(UUID.randomUUID(), "Player", "1.2.3.4")).join();
        assertEquals(Decision.BYPASS, decision.decision());
    }

    @Test
    void falsePositiveCorrectionViaPlayerWhitelist() {
        buildEngine(providerReturning(true, "DE"), CountryRule.disabled(), 100);
        UUID uuid = UUID.randomUUID();
        // Equivalent to /antivpn trust <player>
        whitelistService.addPlayer(uuid, null, "false positive", "staff");
        DecisionResult decision = engine.analyzeConnection(context(uuid, "Player", "1.2.3.4")).join();
        assertEquals(Decision.BYPASS, decision.decision());
    }

    @Test
    void blacklistBlocksWithoutRunningDetection() {
        buildEngine(providerReturning(false, "DE"), CountryRule.disabled(), 100);
        blacklistService.addIp("1.2.3.4", null, "known bad actor", "staff");
        DecisionResult decision = engine.analyzeConnection(context(UUID.randomUUID(), "Player", "1.2.3.4")).join();
        assertEquals(Decision.BLOCK, decision.decision());
        assertNull(decision.aggregatedResult());
    }

    @Test
    void euOnlyBlocksNonEuConnection() {
        CountryRule euOnly = new CountryRule(true, false, false, Set.of(), Set.of(), Set.of(), Set.of(), Set.of());
        buildEngine(providerReturning(false, "US"), euOnly, 100);
        DecisionResult decision = engine.analyzeConnection(context(UUID.randomUUID(), "Player", "1.2.3.4")).join();
        assertEquals(Decision.BLOCK, decision.decision());
        assertEquals(DecisionReason.COUNTRY_NOT_EU, decision.reason());
    }

    @Test
    void euOnlyAllowsEuConnection() {
        CountryRule euOnly = new CountryRule(true, false, false, Set.of(), Set.of(), Set.of(), Set.of(), Set.of());
        buildEngine(providerReturning(false, "DE"), euOnly, 100);
        DecisionResult decision = engine.analyzeConnection(context(UUID.randomUUID(), "Player", "1.2.3.4")).join();
        assertNotEquals(DecisionReason.COUNTRY_NOT_EU, decision.reason());
    }

    @Test
    void rateLimitBlocksExcessiveConnections() {
        buildEngine(providerReturning(false, "DE"), CountryRule.disabled(), 1);
        engine.analyzeConnection(context(UUID.randomUUID(), "Player1", "1.2.3.4")).join();
        DecisionResult second = engine.analyzeConnection(context(UUID.randomUUID(), "Player2", "1.2.3.4")).join();
        assertEquals(Decision.RATE_LIMITED, second.decision());
    }

    @Test
    void temporaryBypassAllowsConnectionDespiteVpn() {
        buildEngine(providerReturning(true, "DE"), CountryRule.disabled(), 100);
        UUID uuid = UUID.randomUUID();
        bypassService.grant(uuid, "1.2.3.4", Duration.ofMinutes(10), "staff bypass", "staff");
        DecisionResult decision = engine.analyzeConnection(context(uuid, "Player", "1.2.3.4")).join();
        assertEquals(Decision.BYPASS, decision.decision());
    }

    @Test
    void cleanConnectionIsCachedAfterFirstCheck() {
        buildEngine(providerReturning(false, "DE"), CountryRule.disabled(), 100);
        engine.analyzeConnection(context(UUID.randomUUID(), "Player1", "8.8.8.8")).join();
        assertTrue(detectionCache.get("8.8.8.8").isPresent());
    }

    @Test
    void sessionResultIsStoredAndRetrievable() {
        buildEngine(providerReturning(false, "DE"), CountryRule.disabled(), 100);
        UUID uuid = UUID.randomUUID();
        DecisionResult decision = engine.analyzeConnection(context(uuid, "Player", "1.2.3.4")).join();
        assertTrue(engine.getSessionResult(uuid).isPresent());
        assertEquals(decision.decision(), engine.getSessionResult(uuid).get().decision());
    }
}
