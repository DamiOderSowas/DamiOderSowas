package de.justsimplenetworks.antivpn.api;

/**
 * Human-readable risk bands derived from a numeric 0-100 risk score produced
 * by the {@code RiskScoringEngine}. Band boundaries match the specification:
 * 0-19 SAFE, 20-39 LOW, 40-59 MEDIUM, 60-79 HIGH, 80-100 CRITICAL.
 */
public enum RiskLevel {
    SAFE(0, 19),
    LOW(20, 39),
    MEDIUM(40, 59),
    HIGH(60, 79),
    CRITICAL(80, 100);

    private final int minInclusive;
    private final int maxInclusive;

    RiskLevel(int minInclusive, int maxInclusive) {
        this.minInclusive = minInclusive;
        this.maxInclusive = maxInclusive;
    }

    public int minInclusive() {
        return minInclusive;
    }

    public int maxInclusive() {
        return maxInclusive;
    }

    /**
     * Resolves the risk level for a given score, clamping out-of-range input
     * into the nearest valid band instead of throwing.
     */
    public static RiskLevel fromScore(int score) {
        int clamped = Math.max(0, Math.min(100, score));
        for (RiskLevel level : values()) {
            if (clamped >= level.minInclusive && clamped <= level.maxInclusive) {
                return level;
            }
        }
        return CRITICAL;
    }
}
