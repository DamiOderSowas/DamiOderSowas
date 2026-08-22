package de.justsimplenetworks.antivpn.api;

/**
 * Why a {@link Decision} was made. Used for logging, metrics, {@code /antivpn debug}
 * output and message-key selection in {@code messages.yml}.
 */
public enum DecisionReason {
    WHITELIST_IP,
    WHITELIST_CIDR,
    WHITELIST_PLAYER,
    BLACKLIST_IP,
    BLACKLIST_CIDR,
    BLACKLIST_PLAYER,
    BLACKLIST_ASN,
    TEMPORARY_BYPASS,
    TEMPORARY_WHITELIST,
    TEMPORARY_BLACKLIST,
    MANUAL_TRUST,
    VPN_DETECTED,
    PROXY_DETECTED,
    HOSTING_DETECTED,
    DATACENTER_DETECTED,
    TOR_DETECTED,
    RESIDENTIAL_PROXY_DETECTED,
    COUNTRY_BLOCKED,
    COUNTRY_NOT_EU,
    COUNTRY_NOT_EUROPE,
    CONTINENT_BLOCKED,
    RATE_LIMITED,
    RISK_SCORE,
    PROVIDER_FALLBACK,
    UNKNOWN,
    NO_RULE_MATCHED
}
