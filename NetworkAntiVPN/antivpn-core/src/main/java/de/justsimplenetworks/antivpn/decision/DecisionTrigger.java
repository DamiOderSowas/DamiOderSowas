package de.justsimplenetworks.antivpn.decision;

/**
 * A condition a {@code DecisionRule} can match against. Detection-flag
 * triggers look at the aggregated verdict; risk-level triggers look at the
 * final {@code RiskLevel} band. {@link #UNKNOWN} matches when detection ran
 * successfully but nothing else matched (the connection looks clean).
 */
public enum DecisionTrigger {
    VPN,
    PROXY,
    HOSTING,
    DATACENTER,
    TOR,
    RESIDENTIAL_PROXY,
    RESIDENTIAL,
    RISK_SAFE,
    RISK_LOW,
    RISK_MEDIUM,
    RISK_HIGH,
    RISK_CRITICAL,
    UNKNOWN
}
