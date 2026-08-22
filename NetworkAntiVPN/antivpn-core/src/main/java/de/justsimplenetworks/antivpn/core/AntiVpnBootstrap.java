package de.justsimplenetworks.antivpn.core;

import de.justsimplenetworks.antivpn.api.event.EventBus;
import de.justsimplenetworks.antivpn.api.event.SimpleEventBus;
import de.justsimplenetworks.antivpn.blacklist.BlacklistService;
import de.justsimplenetworks.antivpn.bypass.BypassService;
import de.justsimplenetworks.antivpn.cache.InMemoryDetectionCache;
import de.justsimplenetworks.antivpn.cache.InMemoryProviderResultCache;
import de.justsimplenetworks.antivpn.config.AntiVpnConfig;
import de.justsimplenetworks.antivpn.config.ConfigLoader;
import de.justsimplenetworks.antivpn.config.MessagesConfig;
import de.justsimplenetworks.antivpn.config.YamlConfig;
import de.justsimplenetworks.antivpn.country.CountryFilterService;
import de.justsimplenetworks.antivpn.decision.DecisionEngine;
import de.justsimplenetworks.antivpn.detection.DetectionAggregator;
import de.justsimplenetworks.antivpn.integration.networkcore.NetworkCoreBridge;
import de.justsimplenetworks.antivpn.integration.networkcore.NoopNetworkCoreBridge;
import de.justsimplenetworks.antivpn.providers.ConfigurableHttpDetectionProvider;
import de.justsimplenetworks.antivpn.providers.DetectionProvider;
import de.justsimplenetworks.antivpn.providers.MockDetectionProvider;
import de.justsimplenetworks.antivpn.providers.ProviderConfig;
import de.justsimplenetworks.antivpn.ratelimit.RateLimiterService;
import de.justsimplenetworks.antivpn.scoring.RiskScoringEngine;
import de.justsimplenetworks.antivpn.security.IpHasher;
import de.justsimplenetworks.antivpn.logging.SafeLogger;
import de.justsimplenetworks.antivpn.whitelist.WhitelistService;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Wires every core component together from {@code config.yml}/{@code messages.yml}
 * on disk into a ready-to-use {@link AntiVpnContext}. Every platform
 * integration module (Velocity/Paper/Purpur/BungeeCord) calls this exact
 * same bootstrap so the pipeline behaves identically on every platform -
 * only the connection listener and command sender adapters differ per platform.
 */
public final class AntiVpnBootstrap {

    private static final SafeLogger LOGGER = SafeLogger.of(AntiVpnBootstrap.class);

    private AntiVpnBootstrap() {
    }

    public static AntiVpnContext bootstrap(Path dataDirectory) {
        return bootstrap(dataDirectory, new NoopNetworkCoreBridge());
    }

    public static AntiVpnContext bootstrap(Path dataDirectory, NetworkCoreBridge networkCoreBridge) {
        YamlConfig configYaml = ConfigLoader.loadOrCreate(dataDirectory, "config.yml");
        YamlConfig messagesYaml = ConfigLoader.loadOrCreate(dataDirectory, "messages.yml");
        AntiVpnConfig config = new AntiVpnConfig(configYaml);
        MessagesConfig messages = new MessagesConfig(messagesYaml);

        EventBus eventBus = new SimpleEventBus();

        RateLimiterService rateLimiterService = new RateLimiterService(
                config.rateLimitEnabled(), config.maxConnectionsPerIp(), config.rateLimitIpWindow(),
                config.maxConnectionsPerPlayer(), config.rateLimitPlayerWindow());

        List<DetectionProvider> providers = buildProviders(config);

        ExecutorService providerExecutor = Executors.newFixedThreadPool(
                Math.max(1, config.maxConcurrentProviderRequests()),
                runnable -> {
                    Thread t = new Thread(runnable, "antivpn-provider-io");
                    t.setDaemon(true);
                    return t;
                });

        InMemoryProviderResultCache providerResultCache = new InMemoryProviderResultCache(
                config.providerCacheMaxEntries(), config.cacheCleanupInterval());

        DetectionAggregator aggregator = new DetectionAggregator(
                providers, providerResultCache, providerExecutor, rateLimiterService,
                config.providerCacheTtl(), config.perProviderTimeout(), config.providerAgreementThreshold());

        InMemoryDetectionCache detectionCache = new InMemoryDetectionCache(
                config.cacheMaxEntries(), config.cacheCleanupInterval());

        RiskScoringEngine scoringEngine = new RiskScoringEngine(config.riskWeights());
        DecisionEngine decisionEngine = new DecisionEngine(config.decisionRuleSet(), config.fallbackPolicy());

        WhitelistService whitelistService = new WhitelistService(dataDirectory.resolve("whitelist.json"));
        whitelistService.reloadConfigEntries(config.whitelistIps(), config.whitelistCidrs(), config.whitelistPlayers());

        BlacklistService blacklistService = new BlacklistService(dataDirectory.resolve("blacklist.json"));
        blacklistService.reloadConfigEntries(config.blacklistIps(), config.blacklistCidrs(),
                config.blacklistPlayers(), config.blacklistAsns());

        BypassService bypassService = new BypassService(dataDirectory.resolve("bypass.json"));

        CountryFilterService countryFilterService = new CountryFilterService(
                config.globalCountryRule(), config.serverCountryRules(), config.serverGroupCountryRules());

        de.justsimplenetworks.antivpn.metrics.MetricsRegistry metrics =
                new de.justsimplenetworks.antivpn.metrics.MetricsRegistry(config.metricsEnabled());

        IpHasher ipHasher = new IpHasher(config.privacyHashSalt());

        AntiVpnEngine engine = new AntiVpnEngine(
                aggregator, detectionCache, scoringEngine, decisionEngine, whitelistService, blacklistService,
                bypassService, countryFilterService, rateLimiterService, metrics, eventBus, networkCoreBridge,
                ipHasher, config, config.cacheEnabled(), config.storeRawIp(), config.cacheTtl(), config.overallAnalysisTimeout());

        LOGGER.info("NetworkAntiVPN core bootstrapped with {} configured provider(s)", providers.size());

        return new AntiVpnContext(engine, config, messages, whitelistService, blacklistService, bypassService,
                detectionCache, metrics, providers, providerExecutor, dataDirectory);
    }

    private static List<DetectionProvider> buildProviders(AntiVpnConfig config) {
        List<DetectionProvider> providers = new ArrayList<>();
        for (ProviderConfig providerConfig : config.providers()) {
            try {
                if (providerConfig.urlTemplate() != null) {
                    providers.add(new ConfigurableHttpDetectionProvider(providerConfig));
                } else {
                    providers.add(new MockDetectionProvider(providerConfig));
                }
            } catch (RuntimeException e) {
                LOGGER.error("Failed to initialize provider '{}', it will be skipped: {}",
                        providerConfig.name(), e.getMessage());
            }
        }
        return providers;
    }
}
