package de.justsimplenetworks.antivpn.integration.velocity;

import com.velocitypowered.api.event.ResultedEvent.ComponentResult;
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.EventTask;
import com.velocitypowered.api.event.connection.DisconnectEvent;
import com.velocitypowered.api.event.connection.LoginEvent;
import com.velocitypowered.api.proxy.Player;
import de.justsimplenetworks.antivpn.api.ConnectionContext;
import de.justsimplenetworks.antivpn.api.Decision;
import de.justsimplenetworks.antivpn.api.DecisionResult;
import de.justsimplenetworks.antivpn.api.Platform;
import de.justsimplenetworks.antivpn.core.AntiVpnContext;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;

import java.net.InetSocketAddress;
import java.time.Instant;
import java.util.concurrent.CompletableFuture;

/**
 * Runs the full NetworkAntiVPN pipeline on {@link LoginEvent} - after
 * authentication (so a real player UUID is known) but before the player is
 * routed to any backend server. This is the "Client -&gt; Velocity -&gt;
 * NetworkAntiVPN -&gt; Connection Analysis -&gt; Decision -&gt; Backend" step from
 * the specification.
 */
public final class VelocityConnectionListener {

    private static final LegacyComponentSerializer LEGACY = LegacyComponentSerializer.legacyAmpersand();

    private final AntiVpnContext context;

    public VelocityConnectionListener(AntiVpnContext context) {
        this.context = context;
    }

    @Subscribe
    public EventTask onLogin(LoginEvent event) {
        if (!context.config().enabled()) {
            return null;
        }
        Player player = event.getPlayer();
        InetSocketAddress remote = player.getRemoteAddress();
        String ip = remote.getAddress().getHostAddress();

        ConnectionContext ctx = new ConnectionContext(player.getUniqueId(), player.getUsername(), ip,
                detectPlatform(player), null, null, ConnectionContext.Stage.PROXY_LOGIN, Instant.now());

        CompletableFuture<Void> future = context.engine().analyzeConnection(ctx)
                .thenAccept(decision -> applyDecision(event, player, decision));
        return EventTask.resumeWhenComplete(future);
    }

    @Subscribe
    public void onDisconnect(DisconnectEvent event) {
        context.engine().clearSessionResult(event.getPlayer().getUniqueId());
    }

    private void applyDecision(LoginEvent event, Player player, DecisionResult decision) {
        if (decision.isBlocked() || decision.decision() == Decision.REQUIRE_VERIFICATION) {
            Component message = LEGACY.deserialize(context.messages().get(decision.messageKey()));
            event.setResult(ComponentResult.denied(message));
        } else if (decision.decision() == Decision.ALLOW_WITH_WARNING) {
            player.sendMessage(LEGACY.deserialize(context.messages().get(decision.messageKey())));
        }
    }

    /**
     * Best-effort Bedrock detection: Velocity has no native concept of a
     * Bedrock client. If a Floodgate-style username prefix is present the
     * connection is treated as Bedrock; otherwise it is treated as Java.
     * Networks running Geyser/Floodgate should adjust this to match their
     * configured prefix (default {@code .}), or wire a real
     * Floodgate-provided platform hint here once available.
     */
    private Platform detectPlatform(Player player) {
        return player.getUsername().startsWith(".") ? Platform.BEDROCK : Platform.JAVA;
    }
}
