package de.justsimplenetworks.antivpn.decision;

import de.justsimplenetworks.antivpn.api.Decision;
import de.justsimplenetworks.antivpn.api.DecisionReason;

/**
 * Maps a {@link Decision}/{@link DecisionReason} pair to the flat
 * {@code messages.yml} key that should be shown to the affected player (see
 * {@code docs/COMMANDS.md} and the shipped {@code messages.yml} for the full
 * key list, e.g. {@code blocked-vpn}, {@code blocked-tor}, {@code rate-limited}).
 * Returns {@code null} when the decision is a silent allow that should only
 * be logged, never shown to the player.
 */
public final class DecisionMessageKeys {

    private DecisionMessageKeys() {
    }

    public static String resolve(Decision decision, DecisionReason reason) {
        return switch (decision) {
            case REQUIRE_VERIFICATION -> "verification-required";
            case RATE_LIMITED -> "rate-limited";
            case BYPASS -> reason == DecisionReason.TEMPORARY_BYPASS ? "bypass-active" : "whitelisted";
            case BLOCK, TEMPORARY_BLOCK -> resolveBlockedKey(reason);
            case ALLOW_WITH_WARNING -> "allowed-warning";
            case ALLOW_WITH_LOG, ALLOW -> null;
        };
    }

    private static String resolveBlockedKey(DecisionReason reason) {
        return switch (reason) {
            case VPN_DETECTED -> "blocked-vpn";
            case PROXY_DETECTED -> "blocked-proxy";
            case TOR_DETECTED -> "blocked-tor";
            case DATACENTER_DETECTED -> "blocked-datacenter";
            case HOSTING_DETECTED -> "blocked-hosting";
            case RESIDENTIAL_PROXY_DETECTED -> "blocked-residential-proxy";
            case COUNTRY_BLOCKED, COUNTRY_NOT_EU, COUNTRY_NOT_EUROPE, CONTINENT_BLOCKED -> "blocked-country";
            case BLACKLIST_IP, BLACKLIST_CIDR, BLACKLIST_PLAYER, BLACKLIST_ASN, TEMPORARY_BLACKLIST -> "blocked-blacklist";
            case RISK_SCORE -> "blocked-risk";
            case PROVIDER_FALLBACK -> "provider-error";
            default -> "blocked-generic";
        };
    }
}
