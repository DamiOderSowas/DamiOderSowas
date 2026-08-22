package de.justsimplenetworks.antivpn.api;

import java.time.Instant;
import java.util.Objects;

/**
 * The final, actionable outcome of the whole pipeline for one connection
 * attempt: what to do ({@link #decision()}), why ({@link #reason()}), and
 * the risk data that led to it. Produced by {@code decision.DecisionEngine}.
 *
 * @param decision           the action to take
 * @param reason             why this decision was made
 * @param riskScore          final 0-100 risk score (0 if the decision short-circuited via whitelist/bypass)
 * @param riskLevel          the {@link RiskLevel} band for {@link #riskScore()}
 * @param aggregatedResult   the detection data behind this decision, or {@code null}
 *                           if the decision short-circuited before detection ran
 * @param messageKey         the {@code messages.yml} key to send to the player/staff
 * @param blockedUntil       for {@link Decision#TEMPORARY_BLOCK}, when the block expires; otherwise {@code null}
 * @param timestamp          when this decision was made
 */
public record DecisionResult(
        Decision decision,
        DecisionReason reason,
        int riskScore,
        RiskLevel riskLevel,
        AggregatedDetectionResult aggregatedResult,
        String messageKey,
        Instant blockedUntil,
        Instant timestamp
) {

    public DecisionResult {
        Objects.requireNonNull(decision, "decision must not be null");
        Objects.requireNonNull(reason, "reason must not be null");
        Objects.requireNonNull(riskLevel, "riskLevel must not be null");
        Objects.requireNonNull(messageKey, "messageKey must not be null");
        Objects.requireNonNull(timestamp, "timestamp must not be null");
    }

    public boolean isAllowed() {
        return decision == Decision.ALLOW
                || decision == Decision.ALLOW_WITH_LOG
                || decision == Decision.ALLOW_WITH_WARNING
                || decision == Decision.BYPASS;
    }

    public boolean isBlocked() {
        return decision == Decision.BLOCK
                || decision == Decision.TEMPORARY_BLOCK
                || decision == Decision.RATE_LIMITED;
    }
}
