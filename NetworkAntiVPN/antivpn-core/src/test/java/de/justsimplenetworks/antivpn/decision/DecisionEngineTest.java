package de.justsimplenetworks.antivpn.decision;

import de.justsimplenetworks.antivpn.api.AggregatedDetectionResult;
import de.justsimplenetworks.antivpn.api.Decision;
import de.justsimplenetworks.antivpn.api.DecisionResult;
import de.justsimplenetworks.antivpn.api.DetectionResult;
import de.justsimplenetworks.antivpn.api.RiskLevel;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class DecisionEngineTest {

    private final DecisionEngine engine = new DecisionEngine(DecisionRuleSet.defaults(), FallbackPolicy.FAIL_OPEN);

    private DetectionResult successfulResult() {
        return DetectionResult.builder("1.2.3.4", "p1").confidence(1.0).build();
    }

    private AggregatedDetectionResult result(boolean vpn, boolean tor, boolean datacenter) {
        return new AggregatedDetectionResult("1.2.3.4", Instant.now(), List.of(successfulResult()), vpn, false,
                false, datacenter, tor, false, false, "DE", "EU", 12345, "ISP", "Org", 1.0, 1.0, List.of(), false);
    }

    @Test
    void torAlwaysBlocks() {
        DecisionResult decision = engine.evaluate(result(false, true, false), 10, RiskLevel.LOW);
        assertEquals(Decision.BLOCK, decision.decision());
    }

    @Test
    void vpnBlocksByDefault() {
        DecisionResult decision = engine.evaluate(result(true, false, false), 10, RiskLevel.LOW);
        assertEquals(Decision.BLOCK, decision.decision());
    }

    @Test
    void datacenterRequiresVerification() {
        DecisionResult decision = engine.evaluate(result(false, false, true), 10, RiskLevel.LOW);
        assertEquals(Decision.REQUIRE_VERIFICATION, decision.decision());
    }

    @Test
    void criticalRiskBlocksEvenWithoutFlags() {
        DecisionResult decision = engine.evaluate(result(false, false, false), 90, RiskLevel.CRITICAL);
        assertEquals(Decision.BLOCK, decision.decision());
    }

    @Test
    void cleanConnectionFallsBackToDefaultDecision() {
        DecisionResult decision = engine.evaluate(result(false, false, false), 5, RiskLevel.SAFE);
        assertEquals(Decision.ALLOW_WITH_LOG, decision.decision());
    }

    @Test
    void fallbackOpenAllowsWhenNoProviderSucceeded() {
        AggregatedDetectionResult unknown = AggregatedDetectionResult.unknown("1.2.3.4", List.of());
        DecisionResult decision = engine.evaluate(unknown, 0, RiskLevel.SAFE);
        assertEquals(Decision.ALLOW_WITH_LOG, decision.decision());
    }

    @Test
    void fallbackClosedBlocksWhenNoProviderSucceeded() {
        DecisionEngine closedEngine = new DecisionEngine(DecisionRuleSet.defaults(), FallbackPolicy.FAIL_CLOSED);
        AggregatedDetectionResult unknown = AggregatedDetectionResult.unknown("1.2.3.4", List.of());
        DecisionResult decision = closedEngine.evaluate(unknown, 0, RiskLevel.SAFE);
        assertEquals(Decision.BLOCK, decision.decision());
    }

    @Test
    void fallbackRequireVerificationWhenNoProviderSucceeded() {
        DecisionEngine verifyEngine = new DecisionEngine(DecisionRuleSet.defaults(), FallbackPolicy.REQUIRE_VERIFICATION);
        AggregatedDetectionResult unknown = AggregatedDetectionResult.unknown("1.2.3.4", List.of());
        DecisionResult decision = verifyEngine.evaluate(unknown, 0, RiskLevel.SAFE);
        assertEquals(Decision.REQUIRE_VERIFICATION, decision.decision());
    }
}
