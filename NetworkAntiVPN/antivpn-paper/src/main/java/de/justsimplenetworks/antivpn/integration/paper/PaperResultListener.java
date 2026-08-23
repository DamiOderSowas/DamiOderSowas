package de.justsimplenetworks.antivpn.integration.paper;

import de.justsimplenetworks.antivpn.core.AntiVpnContext;
import de.justsimplenetworks.antivpn.core.SessionResultPayload;
import org.bukkit.entity.Player;
import org.bukkit.plugin.messaging.PluginMessageListener;

/**
 * Receives the decision Velocity computed at login, forwarded over the
 * {@code networkantivpn:sync} plugin messaging channel (see
 * {@code integration.velocity.ResultForwardingChannel}), and stores it as
 * this JVM's own session result so {@link PaperConnectionListener} (and any
 * later {@code /antivpn} lookup) can reuse it.
 */
public final class PaperResultListener implements PluginMessageListener {

    private final AntiVpnContext context;

    public PaperResultListener(AntiVpnContext context) {
        this.context = context;
    }

    @Override
    public void onPluginMessageReceived(String channel, Player player, byte[] message) {
        try {
            SessionResultPayload.Decoded decoded = SessionResultPayload.decode(message);
            context.engine().storeSessionResult(decoded.playerUuid(), decoded.decision());
        } catch (RuntimeException e) {
            // Malformed/foreign payload on our channel - ignore rather than crash the connection.
        }
    }
}
