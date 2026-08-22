package de.justsimplenetworks.antivpn.integration.networkcore;

import de.justsimplenetworks.antivpn.api.PlayerRiskProfile;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * Default {@link NetworkCoreBridge} used while NetworkCore does not exist
 * yet (or is disabled). Every operation is a safe no-op so the rest of the
 * plugin never has to branch on "is NetworkCore installed".
 */
public final class NoopNetworkCoreBridge implements NetworkCoreBridge {

    @Override
    public boolean isAvailable() {
        return false;
    }

    @Override
    public CompletableFuture<Void> publishRiskProfile(PlayerRiskProfile profile) {
        return CompletableFuture.completedFuture(null);
    }

    @Override
    public CompletableFuture<UUID> resolveCanonicalIdentity(UUID playerUuid) {
        return CompletableFuture.completedFuture(playerUuid);
    }
}
