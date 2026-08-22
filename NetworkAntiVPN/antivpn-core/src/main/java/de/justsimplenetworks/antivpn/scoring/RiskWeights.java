package de.justsimplenetworks.antivpn.scoring;

import java.util.EnumMap;
import java.util.Map;

/**
 * Configurable point contribution of each {@link RiskFactor} toward the
 * final 0-100 risk score. Loaded from {@code risk-scoring.weights} in
 * {@code config.yml}; {@link #defaults()} is used for any factor the
 * operator did not explicitly configure.
 */
public final class RiskWeights {

    private final Map<RiskFactor, Integer> weights;

    public RiskWeights(Map<RiskFactor, Integer> weights) {
        this.weights = new EnumMap<>(RiskFactor.class);
        this.weights.putAll(defaults().weights);
        this.weights.putAll(weights);
    }

    private RiskWeights(Map<RiskFactor, Integer> weights, boolean skipMerge) {
        this.weights = new EnumMap<>(weights);
    }

    public int get(RiskFactor factor) {
        return weights.getOrDefault(factor, 0);
    }

    public static RiskWeights defaults() {
        Map<RiskFactor, Integer> defaults = new EnumMap<>(RiskFactor.class);
        defaults.put(RiskFactor.VPN, 45);
        defaults.put(RiskFactor.PROXY, 40);
        defaults.put(RiskFactor.TOR, 60);
        defaults.put(RiskFactor.DATACENTER, 30);
        defaults.put(RiskFactor.HOSTING, 25);
        defaults.put(RiskFactor.RESIDENTIAL_PROXY, 50);
        defaults.put(RiskFactor.IP_REPUTATION, 20);
        defaults.put(RiskFactor.PROVIDER_AGREEMENT, 10);
        defaults.put(RiskFactor.PROVIDER_DISAGREEMENT, 5);
        defaults.put(RiskFactor.COUNTRY_RISK, 15);
        defaults.put(RiskFactor.HISTORICAL_DETECTION, 15);
        return new RiskWeights(defaults, true);
    }
}
