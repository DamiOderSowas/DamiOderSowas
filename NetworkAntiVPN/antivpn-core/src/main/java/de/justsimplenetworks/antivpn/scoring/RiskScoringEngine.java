package de.justsimplenetworks.antivpn.scoring;

import de.justsimplenetworks.antivpn.api.AggregatedDetectionResult;
import de.justsimplenetworks.antivpn.api.DetectionResult;
import de.justsimplenetworks.antivpn.api.RiskLevel;

import java.util.Objects;
import java.util.OptionalDouble;

/**
 * Turns an {@link AggregatedDetectionResult} plus additional context into a
 * final 0-100 risk score and its {@link RiskLevel} band. Every contribution
 * is driven by {@link RiskWeights}, which is fully configurable via
 * {@code config.yml} - no risk decision is hard-coded here.
 *
 * <p>High-risk-country and disagreement thresholds are also configurable
 * so operators can tune sensitivity for their community without a code change.
 */
public final class RiskScoringEngine {

    private static final double HIGH_AGREEMENT_THRESHOLD = 0.8;
    private static final double LOW_AGREEMENT_THRESHOLD = 0.5;
    private static final int HISTORICAL_HIGH_RISK_THRESHOLD = RiskLevel.HIGH.minInclusive();

    private volatile RiskWeights weights;

    public RiskScoringEngine(RiskWeights weights) {
        this.weights = Objects.requireNonNull(weights, "weights must not be null");
    }

    /** Swaps in freshly configured weights, e.g. from {@code /antivpn reload}. Safe to call from any thread. */
    public void reload(RiskWeights weights) {
        this.weights = Objects.requireNonNull(weights, "weights must not be null");
    }

    public int score(AggregatedDetectionResult result, ScoringContext context) {
        Objects.requireNonNull(result, "result must not be null");
        Objects.requireNonNull(context, "context must not be null");

        int score = 0;
        if (result.vpn()) score += weights.get(RiskFactor.VPN);
        if (result.proxy()) score += weights.get(RiskFactor.PROXY);
        if (result.hosting()) score += weights.get(RiskFactor.HOSTING);
        if (result.datacenter()) score += weights.get(RiskFactor.DATACENTER);
        if (result.tor()) score += weights.get(RiskFactor.TOR);
        if (result.residentialProxy()) score += weights.get(RiskFactor.RESIDENTIAL_PROXY);

        OptionalDouble avgProviderRisk = result.providerResults().stream()
                .filter(r -> !r.isFailed() && r.riskScore() != null)
                .mapToInt(DetectionResult::riskScore)
                .average();
        if (avgProviderRisk.isPresent()) {
            score += (int) Math.round(avgProviderRisk.getAsDouble() / 100.0 * weights.get(RiskFactor.IP_REPUTATION));
        }

        if (result.isSuspicious()) {
            if (result.providerAgreement() >= HIGH_AGREEMENT_THRESHOLD) {
                score += weights.get(RiskFactor.PROVIDER_AGREEMENT);
            } else if (result.providerAgreement() < LOW_AGREEMENT_THRESHOLD) {
                score += weights.get(RiskFactor.PROVIDER_DISAGREEMENT);
            }
        }

        if (context.countryConsideredHighRisk()) {
            score += weights.get(RiskFactor.COUNTRY_RISK);
        }

        if (context.previousRiskScore() != null && context.previousRiskScore() >= HISTORICAL_HIGH_RISK_THRESHOLD) {
            score += weights.get(RiskFactor.HISTORICAL_DETECTION);
        }

        return Math.max(0, Math.min(100, score));
    }

    public RiskLevel level(int score) {
        return RiskLevel.fromScore(score);
    }
}
