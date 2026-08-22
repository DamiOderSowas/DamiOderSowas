package de.justsimplenetworks.antivpn.integration.networkcore;

import de.justsimplenetworks.antivpn.api.PlayerRiskProfile;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * Seam for a future NetworkCore integration. NetworkCore does not exist yet
 * (it is still under active development), so NetworkAntiVPN must run fully
 * standalone today - nothing in this module may depend on NetworkCore
 * classes or network calls. This interface is the only place a future
 * NetworkCore adapter needs to plug into.
 * <p>
 * Once NetworkCore exists, an implementation can bridge into (at minimum):
 * {@code NetworkIdentity} (canonical player identity across the network),
 * {@code NetworkPlayerData} (persisted player records), {@code NetworkSessions}
 * (cross-server session tracking), {@code NetworkPresence}, {@code NetworkPermissions},
 * {@code NetworkRanks}, {@code NetworkServer}/{@code NetworkServerGroups}/{@code NetworkServerStatus},
 * {@code NetworkRouting}/{@code NetworkQueue}/{@code NetworkProxy}/{@code NetworkTransfer},
 * {@code NetworkSecurity}/{@code NetworkPunishments}/{@code NetworkAudit},
 * {@code NetworkDatabase}/{@code NetworkCache}/{@code NetworkRedis} (for a shared,
 * cross-node detection cache instead of the in-memory one), {@code NetworkConfiguration}/
 * {@code NetworkSecrets} (for provider API keys instead of environment variables),
 * {@code NetworkMetrics}/{@code NetworkLogging}/{@code NetworkHealth}, and
 * {@code NetworkAntiMultiAccount} (the primary consumer of {@link PlayerRiskProfile}).
 * See {@code docs/INTEGRATION.md} for the planned adapter shape.
 */
public interface NetworkCoreBridge {

    /** Whether a real NetworkCore connection is currently available. */
    boolean isAvailable();

    /**
     * Publishes a connection's risk profile toward NetworkCore /
     * NetworkAntiMultiAccount. The default {@link NoopNetworkCoreBridge}
     * does nothing; a real bridge would forward this over NetworkCore's
     * messaging/database layer.
     */
    CompletableFuture<Void> publishRiskProfile(PlayerRiskProfile profile);

    /**
     * Looks up a NetworkCore-known canonical identity for a player, if
     * NetworkCore is available. Used to resolve alt-account linkage that
     * this module does not itself implement.
     */
    CompletableFuture<UUID> resolveCanonicalIdentity(UUID playerUuid);
}
