package de.justsimplenetworks.antivpn.decision;

import de.justsimplenetworks.antivpn.api.*;

import java.time.Instant;
import java.util.Objects;

/**
 * Turns an {@link AggregatedDetectionResult} and its risk score into a final
 * {@link DecisionResult} by evaluating a configurable {@link DecisionRuleSet}.
 * No rule is hard-coded: everything comes from {@code config.yml} (or the
 * documented defaults in {@link DecisionRuleSet#defaults()}).
 * <p>
 * This engine only runs the "did we successfully analyze the connection"
 * path. Short-circuiting decisions (whitelist, blacklist, temporary bypass,
 * country filter, rate limit) are resolved earlier in
 * {@code core.AntiVpnEngine} and never reach here.
 */
public final class DecisionEngine {

    private volatile DecisionRuleSet ruleSet;
    private volatile FallbackPolicy fallbackPolicy;

    public DecisionEngine(DecisionRuleSet ruleSet, FallbackPolicy fallbackPolicy) {
        this.ruleSet = Objects.requireNonNull(ruleSet, "ruleSet must not be null");
        this.fallbackPolicy = Objects.requireNonNull(fallbackPolicy, "fallbackPolicy must not be null");
    }

    /** Swaps in a freshly configured rule set and fallback policy, e.g. from {@code /antivpn reload}. */
    public void reload(DecisionRuleSet ruleSet, FallbackPolicy fallbackPolicy) {
        this.ruleSet = Objects.requireNonNull(ruleSet, "ruleSet must not be null");
        this.fallbackPolicy = Objects.requireNonNull(fallbackPolicy, "fallbackPolicy must not be null");
    }

    public DecisionResult evaluate(AggregatedDetectionResult result, int riskScore, RiskLevel riskLevel) {
        Objects.requireNonNull(result, "result must not be null");
        Objects.requireNonNull(riskLevel, "riskLevel must not be null");

        if (result.successfulProviderCount() == 0) {
            return fallbackDecision(result, riskScore, riskLevel);
        }

        for (DecisionRule rule : ruleSet.rules()) {
            if (matches(rule.trigger(), result, riskLevel)) {
                return build(rule.decision(), rule.reason(), riskScore, riskLevel, result);
            }
        }

        return build(ruleSet.defaultDecision(), ruleSet.defaultReason(), riskScore, riskLevel, result);
    }

    private boolean matches(DecisionTrigger trigger, AggregatedDetectionResult result, RiskLevel riskLevel) {
        return switch (trigger) {
            case VPN -> result.vpn();
            case PROXY -> result.proxy();
            case HOSTING -> result.hosting();
            case DATACENTER -> result.datacenter();
            case TOR -> result.tor();
            case RESIDENTIAL_PROXY -> result.residentialProxy();
            case RESIDENTIAL -> result.residential();
            case RISK_SAFE -> riskLevel == RiskLevel.SAFE;
            case RISK_LOW -> riskLevel == RiskLevel.LOW;
            case RISK_MEDIUM -> riskLevel == RiskLevel.MEDIUM;
            case RISK_HIGH -> riskLevel == RiskLevel.HIGH;
            case RISK_CRITICAL -> riskLevel == RiskLevel.CRITICAL;
            case UNKNOWN -> !result.isSuspicious();
        };
    }

    private DecisionResult fallbackDecision(AggregatedDetectionResult result, int riskScore, RiskLevel riskLevel) {
        Decision decision = switch (fallbackPolicy) {
            case FAIL_OPEN -> Decision.ALLOW_WITH_LOG;
            case FAIL_CLOSED -> Decision.BLOCK;
            case REQUIRE_VERIFICATION -> Decision.REQUIRE_VERIFICATION;
        };
        return build(decision, DecisionReason.PROVIDER_FALLBACK, riskScore, riskLevel, result);
    }

    private DecisionResult build(Decision decision, DecisionReason reason, int riskScore, RiskLevel riskLevel,
                                  AggregatedDetectionResult result) {
        String messageKey = messageKeyFor(decision, reason);
        Instant blockedUntil = null;
        return new DecisionResult(decision, reason, riskScore, riskLevel, result, messageKey, blockedUntil, Instant.now());
    }

    private String messageKeyFor(Decision decision, DecisionReason reason) {
        String key = DecisionMessageKeys.resolve(decision, reason);
        return key == null ? "allowed-silent" : key;
    }
}
