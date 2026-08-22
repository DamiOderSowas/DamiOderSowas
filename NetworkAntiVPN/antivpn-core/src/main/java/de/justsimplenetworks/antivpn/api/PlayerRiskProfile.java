package de.justsimplenetworks.antivpn.api;

import java.time.Instant;
import java.util.UUID;

/**
 * A privacy-conscious summary of one connection, exposed for other
 * JustSimpleNetworks systems - most importantly the future
 * NetworkAntiMultiAccount project, which enforces "max accounts per IP"
 * rules and needs this data without depending on this module's internals.
 * <p>
 * {@link #ipHash()} is always populated; {@link #ip()} is only populated
 * when {@code privacy.store-raw-ip} is enabled in {@code config.yml} (see
 * {@code docs/PRIVACY.md}). Consumers that only need to correlate accounts
 * should prefer {@link #ipHash()} over the raw IP.
 *
 * @param playerUuid        the player's UUID
 * @param playerName        the player's username at connection time
 * @param ip                raw IP address, or {@code null} if privacy mode omits it
 * @param ipHash             salted hash of the IP address (see {@code security.IpHasher})
 * @param country            resolved country code, or {@code null} if unknown
 * @param asn                resolved ASN, or {@code null} if unknown
 * @param riskScore          final 0-100 risk score for this connection
 * @param platform           {@link Platform#JAVA} or {@link Platform#BEDROCK}
 * @param connectionTimestamp when the connection was observed
 */
public record PlayerRiskProfile(
        UUID playerUuid,
        String playerName,
        String ip,
        String ipHash,
        String country,
        Integer asn,
        int riskScore,
        Platform platform,
        Instant connectionTimestamp
) {
}
