package de.justsimplenetworks.antivpn.scoring;

/**
 * A single contributor to the final 0-100 risk score. Each factor has a
 * configurable weight in {@code risk-scoring.weights} in {@code config.yml};
 * see {@code docs/RISK-SCORING.md} for the full list and default values.
 */
public enum RiskFactor {
    VPN,
    PROXY,
    HOSTING,
    DATACENTER,
    TOR,
    RESIDENTIAL_PROXY,
    IP_REPUTATION,
    PROVIDER_AGREEMENT,
    PROVIDER_DISAGREEMENT,
    COUNTRY_RISK,
    HISTORICAL_DETECTION
}
