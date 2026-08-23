package de.justsimplenetworks.antivpn.integration.velocity;

import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.player.ServerConnectedEvent;
import com.velocitypowered.api.proxy.Player;
import de.justsimplenetworks.antivpn.api.DecisionResult;
import de.justsimplenetworks.antivpn.core.AntiVpnContext;
import de.justsimplenetworks.antivpn.core.SessionResultPayload;

import java.util.Optional;

/**
 * Forwards the decision already computed at login to whichever backend
 * server the player ends up on, via a plugin messaging channel, so
 * Paper/Purpur can skip a redundant provider check for the same session
 * (see {@code docs/VELOCITY.md} and {@code docs/PAPER.md}).
 */
public final class ResultForwardingChannel {

    private final AntiVpnContext context;

    public ResultForwardingChannel(AntiVpnContext context) {
        this.context = context;
    }

    @Subscribe
    public void onServerConnected(ServerConnectedEvent event) {
        Player player = event.getPlayer();
        Optional<DecisionResult> decision = context.engine().getSessionResult(player.getUniqueId());
        decision.ifPresent(result -> {
            byte[] payload = SessionResultPayload.encode(player.getUniqueId(), result);
            player.sendPluginMessage(VelocityAntiVpnPlugin.RESULT_CHANNEL, payload);
        });
    }
}
