package de.justsimplenetworks.antivpn.scoring;

import de.justsimplenetworks.antivpn.api.AggregatedDetectionResult;
import de.justsimplenetworks.antivpn.api.RiskLevel;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class RiskScoringEngineTest {

    private final RiskScoringEngine engine = new RiskScoringEngine(RiskWeights.defaults());

    private AggregatedDetectionResult result(boolean vpn, boolean proxy, boolean tor, double agreement) {
        return new AggregatedDetectionResult("1.2.3.4", Instant.now(), List.of(), vpn, proxy, false, false,
                tor, false, false, "DE", "EU", 12345, "ISP", "Org", 1.0, agreement, List.of(), false);
    }

    @Test
    void cleanConnectionScoresLow() {
        int score = engine.score(result(false, false, false, 1.0), ScoringContext.empty());
        assertEquals(RiskLevel.SAFE, engine.level(score));
    }

    @Test
    void vpnDetectionRaisesScore() {
        int score = engine.score(result(true, false, false, 1.0), ScoringContext.empty());
        assertTrue(score > 0);
    }

    @Test
    void torDetectionScoresHigherThanVpnAlone() {
        int torScore = engine.score(result(false, false, true, 1.0), ScoringContext.empty());
        int vpnScore = engine.score(result(true, false, false, 1.0), ScoringContext.empty());
        assertTrue(torScore >= vpnScore);
    }

    @Test
    void multipleFlagsCombineTowardCritical() {
        int score = engine.score(result(true, true, true, 1.0), ScoringContext.empty());
        assertEquals(RiskLevel.CRITICAL, engine.level(score));
    }

    @Test
    void countryRiskContextIncreasesScore() {
        int base = engine.score(result(false, false, false, 1.0), ScoringContext.empty());
        int withCountryRisk = engine.score(result(false, false, false, 1.0), new ScoringContext(null, true));
        assertTrue(withCountryRisk > base);
    }

    @Test
    void historicalHighRiskIncreasesScore() {
        int base = engine.score(result(false, false, false, 1.0), ScoringContext.empty());
        int withHistory = engine.score(result(false, false, false, 1.0), new ScoringContext(90, false));
        assertTrue(withHistory > base);
    }

    @Test
    void scoreIsClampedToValidRange() {
        int score = engine.score(result(true, true, true, 1.0), new ScoringContext(100, true));
        assertTrue(score >= 0 && score <= 100);
    }
}
