package de.justsimplenetworks.antivpn.integration.bungeecord;

import de.justsimplenetworks.antivpn.api.DecisionResult;
import de.justsimplenetworks.antivpn.core.AntiVpnContext;
import de.justsimplenetworks.antivpn.core.SessionResultPayload;
import net.md_5.bungee.api.event.ServerConnectedEvent;
import net.md_5.bungee.api.plugin.Listener;
import net.md_5.bungee.event.EventHandler;

import java.util.Optional;
import java.util.UUID;

/**
 * BungeeCord equivalent of {@code integration.velocity.ResultForwardingChannel} -
 * see that class's Javadoc for the exact timing tradeoffs of forwarding a
 * result this way.
 */
public final class ResultForwardingListener implements Listener {

    private final AntiVpnContext context;

    public ResultForwardingListener(AntiVpnContext context) {
        this.context = context;
    }

    @EventHandler
    public void onServerConnected(ServerConnectedEvent event) {
        UUID uuid = event.getPlayer().getUniqueId();
        Optional<DecisionResult> decision = context.engine().getSessionResult(uuid);
        decision.ifPresent(result -> {
            byte[] payload = SessionResultPayload.encode(uuid, result);
            event.getServer().sendData(BungeeAntiVpnPlugin.RESULT_CHANNEL, payload);
        });
    }
}
