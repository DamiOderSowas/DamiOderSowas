package de.justsimplenetworks.antivpn.config;

import de.justsimplenetworks.antivpn.api.Decision;
import de.justsimplenetworks.antivpn.api.DecisionReason;
import de.justsimplenetworks.antivpn.country.CountryRule;
import de.justsimplenetworks.antivpn.decision.DecisionRule;
import de.justsimplenetworks.antivpn.decision.DecisionRuleSet;
import de.justsimplenetworks.antivpn.decision.DecisionTrigger;
import de.justsimplenetworks.antivpn.decision.FallbackPolicy;
import de.justsimplenetworks.antivpn.providers.ProviderCapability;
import de.justsimplenetworks.antivpn.providers.ProviderConfig;
import de.justsimplenetworks.antivpn.scoring.RiskFactor;
import de.justsimplenetworks.antivpn.scoring.RiskWeights;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Translates the raw {@code config.yml} tree into the strongly-typed domain
 * objects the rest of NetworkAntiVPN is built from (risk weights, decision
 * rules, provider configs, country rules, ...). This is the single place
 * that knows the YAML schema; every other class only ever sees typed values.
 */
public final class AntiVpnConfig {

    private static final Logger LOGGER = LoggerFactory.getLogger(AntiVpnConfig.class);

    private volatile YamlConfig yaml;

    public AntiVpnConfig(YamlConfig yaml) {
        this.yaml = yaml;
    }

    /** Swaps in freshly loaded YAML, e.g. from {@code /antivpn reload}. Safe to call from any thread. */
    public void reload(YamlConfig newYaml) {
        this.yaml = newYaml;
    }

    public boolean enabled() {
        return yaml.getBoolean("general.enabled", true);
    }

    public boolean debug() {
        return yaml.getBoolean("general.debug", false);
    }

    // ---- detection ----

    public double providerAgreementThreshold() {
        return yaml.getDouble("detection.provider-agreement-threshold", 0.5);
    }

    public Duration perProviderTimeout() {
        return Duration.ofSeconds(yaml.getInt("detection.per-provider-timeout-seconds", 3));
    }

    public int maxConcurrentProviderRequests() {
        return yaml.getInt("detection.max-concurrent-provider-requests", 16);
    }

    public Duration overallAnalysisTimeout() {
        return Duration.ofSeconds(yaml.getInt("timeouts.overall-analysis-seconds", 5));
    }

    // ---- providers ----

    public List<ProviderConfig> providers() {
        List<ProviderConfig> result = new ArrayList<>();
        YamlConfig providersSection = yaml.section("providers");
        for (String name : providersSection.keys("")) {
            YamlConfig p = providersSection.section(name);
            Set<ProviderCapability> capabilities = new LinkedHashSet<>();
            for (String cap : p.getStringList("capabilities")) {
                try {
                    capabilities.add(ProviderCapability.valueOf(cap.toUpperCase(Locale.ROOT)));
                } catch (IllegalArgumentException e) {
                    LOGGER.warn("Unknown provider capability '{}' for provider '{}', ignoring", cap, name);
                }
            }
            result.add(new ProviderConfig(
                    name,
                    p.getBoolean("enabled", false),
                    p.getInt("priority", 100),
                    p.getDouble("weight", 1.0),
                    Duration.ofSeconds(p.getInt("timeout-seconds", 3)),
                    p.getInt("rate-limit-per-minute", 0),
                    capabilities,
                    blankToNull(p.getString("url-template", null)),
                    blankToNull(p.getString("api-key-env", null)),
                    blankToNull(p.getString("api-key-header", null)),
                    blankToNull(p.getString("api-key-query-param", null)),
                    p.getStringMap("field-mapping")
            ));
        }
        return result;
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }

    // ---- cache ----

    public boolean cacheEnabled() {
        return yaml.getBoolean("cache.enabled", true);
    }

    public Duration cacheTtl() {
        return Duration.ofSeconds(yaml.getInt("cache.ttl-seconds", 3600));
    }

    public int cacheMaxEntries() {
        return yaml.getInt("cache.max-entries", 10_000);
    }

    public Duration cacheCleanupInterval() {
        return Duration.ofSeconds(yaml.getInt("cache.cleanup-interval-seconds", 60));
    }

    public Duration providerCacheTtl() {
        return Duration.ofSeconds(yaml.getInt("cache.provider-cache-ttl-seconds", 3600));
    }

    public int providerCacheMaxEntries() {
        return yaml.getInt("cache.provider-cache-max-entries", 20_000);
    }

    // ---- risk scoring ----

    public RiskWeights riskWeights() {
        Map<String, Integer> raw = yaml.getIntMap("risk-scoring.weights");
        Map<RiskFactor, Integer> weights = new EnumMap<>(RiskFactor.class);
        for (Map.Entry<String, Integer> entry : raw.entrySet()) {
            try {
                weights.put(RiskFactor.valueOf(entry.getKey().toUpperCase(Locale.ROOT)), entry.getValue());
            } catch (IllegalArgumentException e) {
                LOGGER.warn("Unknown risk factor '{}' in risk-scoring.weights, ignoring", entry.getKey());
            }
        }
        return new RiskWeights(weights);
    }

    // ---- decisions ----

    public DecisionRuleSet decisionRuleSet() {
        List<Map<String, Object>> rawRules = yaml.getMapList("decisions.rules");
        if (rawRules.isEmpty()) {
            return DecisionRuleSet.defaults();
        }
        List<DecisionRule> rules = new ArrayList<>();
        for (Map<String, Object> rawRule : rawRules) {
            try {
                DecisionTrigger trigger = DecisionTrigger.valueOf(String.valueOf(rawRule.get("trigger")).toUpperCase(Locale.ROOT));
                Decision decision = Decision.valueOf(String.valueOf(rawRule.get("decision")).toUpperCase(Locale.ROOT));
                DecisionReason reason = rawRule.containsKey("reason")
                        ? DecisionReason.valueOf(String.valueOf(rawRule.get("reason")).toUpperCase(Locale.ROOT))
                        : defaultReasonFor(trigger);
                rules.add(new DecisionRule(trigger, decision, reason));
            } catch (IllegalArgumentException | NullPointerException e) {
                LOGGER.warn("Skipping malformed decision rule: {}", rawRule, e);
            }
        }
        Decision defaultDecision = parseOr(yaml.getString("decisions.default-decision", "ALLOW_WITH_LOG"),
                Decision.class, Decision.ALLOW_WITH_LOG);
        return new DecisionRuleSet(rules, defaultDecision, DecisionReason.UNKNOWN);
    }

    private DecisionReason defaultReasonFor(DecisionTrigger trigger) {
        return switch (trigger) {
            case VPN -> DecisionReason.VPN_DETECTED;
            case PROXY -> DecisionReason.PROXY_DETECTED;
            case HOSTING -> DecisionReason.HOSTING_DETECTED;
            case DATACENTER -> DecisionReason.DATACENTER_DETECTED;
            case TOR -> DecisionReason.TOR_DETECTED;
            case RESIDENTIAL_PROXY -> DecisionReason.RESIDENTIAL_PROXY_DETECTED;
            case RISK_SAFE, RISK_LOW, RISK_MEDIUM, RISK_HIGH, RISK_CRITICAL -> DecisionReason.RISK_SCORE;
            case RESIDENTIAL, UNKNOWN -> DecisionReason.UNKNOWN;
        };
    }

    public FallbackPolicy fallbackPolicy() {
        return parseOr(yaml.getString("fallback.policy", "FAIL_OPEN"), FallbackPolicy.class, FallbackPolicy.FAIL_OPEN);
    }

    private <E extends Enum<E>> E parseOr(String text, Class<E> type, E fallback) {
        try {
            return Enum.valueOf(type, text.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            LOGGER.warn("Invalid value '{}' for {}, using default {}", text, type.getSimpleName(), fallback);
            return fallback;
        }
    }

    // ---- countries ----

    public CountryRule globalCountryRule() {
        return countryRule("countries");
    }

    public Map<String, CountryRule> serverCountryRules() {
        return namedCountryRules("countries.servers");
    }

    public Map<String, CountryRule> serverGroupCountryRules() {
        return namedCountryRules("countries.server-groups");
    }

    private Map<String, CountryRule> namedCountryRules(String basePath) {
        Map<String, CountryRule> result = new LinkedHashMap<>();
        YamlConfig section = yaml.section(basePath);
        for (String name : section.keys("")) {
            result.put(name, countryRuleFrom(section.section(name)));
        }
        return result;
    }

    private CountryRule countryRule(String path) {
        return countryRuleFrom(yaml.section(path));
    }

    private CountryRule countryRuleFrom(YamlConfig c) {
        return new CountryRule(
                c.getBoolean("eu-only", false),
                c.getBoolean("europe-only", false),
                c.getBoolean("block-unknown-country", false),
                new LinkedHashSet<>(upper(c.getStringList("allowed-countries"))),
                new LinkedHashSet<>(upper(c.getStringList("blocked-countries"))),
                new LinkedHashSet<>(upper(c.getStringList("allowed-continents"))),
                new LinkedHashSet<>(upper(c.getStringList("blocked-continents"))),
                new LinkedHashSet<>(upper(c.getStringList("high-risk-countries")))
        );
    }

    private List<String> upper(List<String> values) {
        return values.stream().map(v -> v.toUpperCase(Locale.ROOT)).toList();
    }

    // ---- whitelist / blacklist (static config-sourced entries) ----

    public List<String> whitelistIps() {
        return yaml.getStringList("whitelist.ips");
    }

    public List<String> whitelistCidrs() {
        return yaml.getStringList("whitelist.cidrs");
    }

    public List<String> whitelistPlayers() {
        return mergePlayerLists(yaml.getStringList("whitelist.players"), yaml.getStringList("whitelist.uuids"));
    }

    public List<String> blacklistIps() {
        return yaml.getStringList("blacklist.ips");
    }

    public List<String> blacklistCidrs() {
        return yaml.getStringList("blacklist.cidrs");
    }

    public List<String> blacklistPlayers() {
        return mergePlayerLists(yaml.getStringList("blacklist.players"), yaml.getStringList("blacklist.uuids"));
    }

    public List<Integer> blacklistAsns() {
        return yaml.getIntList("blacklist.asns");
    }

    private List<String> mergePlayerLists(List<String> a, List<String> b) {
        List<String> merged = new ArrayList<>(a);
        merged.addAll(b);
        return merged;
    }

    // ---- temporary bypass ----

    public boolean temporaryBypassEnabled() {
        return yaml.getBoolean("temporary-bypass.enabled", true);
    }

    // ---- rate limit ----

    public boolean rateLimitEnabled() {
        return yaml.getBoolean("rate-limit.enabled", true);
    }

    public int maxConnectionsPerIp() {
        return yaml.getInt("rate-limit.max-connections-per-ip", 5);
    }

    public Duration rateLimitIpWindow() {
        return Duration.ofSeconds(yaml.getInt("rate-limit.window-seconds", 10));
    }

    public int maxConnectionsPerPlayer() {
        return yaml.getInt("rate-limit.max-connections-per-player", 10);
    }

    public Duration rateLimitPlayerWindow() {
        return Duration.ofSeconds(yaml.getInt("rate-limit.player-window-seconds", 10));
    }

    // ---- privacy ----

    public boolean storeRawIp() {
        return yaml.getBoolean("privacy.store-raw-ip", false);
    }

    public String privacyHashSalt() {
        return yaml.getString("privacy.hash-salt", "change-me-please-set-a-real-salt");
    }

    public int privacyRetentionDays() {
        return yaml.getInt("privacy.retention-days", 7);
    }

    // ---- metrics / logging ----

    public boolean metricsEnabled() {
        return yaml.getBoolean("metrics.enabled", true);
    }

    // ---- platform toggles ----

    public boolean velocityEarlyCheck() {
        return yaml.getBoolean("velocity.early-check", true);
    }

    public boolean velocityForwardResultToBackend() {
        return yaml.getBoolean("velocity.forward-result-to-backend", true);
    }

    public boolean paperReuseProxyResult() {
        return yaml.getBoolean("paper.reuse-proxy-result", true);
    }

    public boolean bungeecordEarlyCheck() {
        return yaml.getBoolean("bungeecord.early-check", true);
    }

    public YamlConfig raw() {
        return yaml;
    }
}
