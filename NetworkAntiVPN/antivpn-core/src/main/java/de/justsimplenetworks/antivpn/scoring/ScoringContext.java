package de.justsimplenetworks.antivpn.scoring;

/**
 * Extra signal the {@link RiskScoringEngine} cannot derive from a single
 * {@code AggregatedDetectionResult} alone, supplied by the caller
 * (typically {@code core.AntiVpnEngine}).
 *
 * @param previousRiskScore   the last known risk score for this IP/player, or {@code null}
 * @param countryConsideredHighRisk whether {@code country.CountryFilterService} marked
 *                                  the resolved country as elevated risk
 */
public record ScoringContext(Integer previousRiskScore, boolean countryConsideredHighRisk) {

    public static ScoringContext empty() {
        return new ScoringContext(null, false);
    }
}
