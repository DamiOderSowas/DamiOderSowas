package de.justsimplenetworks.antivpn.decision;

import de.justsimplenetworks.antivpn.api.Decision;
import de.justsimplenetworks.antivpn.api.DecisionReason;

import java.util.List;

/**
 * An ordered list of {@link DecisionRule}s plus the fallback used when none
 * match. Rules are evaluated top to bottom; the first match wins - order in
 * {@code config.yml} therefore matters and is preserved exactly as configured.
 */
public record DecisionRuleSet(List<DecisionRule> rules, Decision defaultDecision, DecisionReason defaultReason) {

    public DecisionRuleSet {
        rules = List.copyOf(rules);
    }

    /** The example rule set from the specification / shipped {@code config.yml}. */
    public static DecisionRuleSet defaults() {
        return new DecisionRuleSet(List.of(
                new DecisionRule(DecisionTrigger.TOR, Decision.BLOCK, DecisionReason.TOR_DETECTED),
                new DecisionRule(DecisionTrigger.VPN, Decision.BLOCK, DecisionReason.VPN_DETECTED),
                new DecisionRule(DecisionTrigger.PROXY, Decision.BLOCK, DecisionReason.PROXY_DETECTED),
                new DecisionRule(DecisionTrigger.RESIDENTIAL_PROXY, Decision.REQUIRE_VERIFICATION, DecisionReason.RESIDENTIAL_PROXY_DETECTED),
                new DecisionRule(DecisionTrigger.DATACENTER, Decision.REQUIRE_VERIFICATION, DecisionReason.DATACENTER_DETECTED),
                new DecisionRule(DecisionTrigger.HOSTING, Decision.REQUIRE_VERIFICATION, DecisionReason.HOSTING_DETECTED),
                new DecisionRule(DecisionTrigger.RISK_CRITICAL, Decision.BLOCK, DecisionReason.RISK_SCORE),
                new DecisionRule(DecisionTrigger.RISK_HIGH, Decision.REQUIRE_VERIFICATION, DecisionReason.RISK_SCORE),
                new DecisionRule(DecisionTrigger.RISK_MEDIUM, Decision.ALLOW_WITH_WARNING, DecisionReason.RISK_SCORE)
        ), Decision.ALLOW_WITH_LOG, DecisionReason.UNKNOWN);
    }
}
