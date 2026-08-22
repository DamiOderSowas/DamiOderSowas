package de.justsimplenetworks.antivpn.integration.bungeecord;

import de.justsimplenetworks.antivpn.api.ConnectionContext;
import de.justsimplenetworks.antivpn.api.Decision;
import de.justsimplenetworks.antivpn.api.DecisionResult;
import de.justsimplenetworks.antivpn.api.Platform;
import de.justsimplenetworks.antivpn.core.AntiVpnContext;
import net.md_5.bungee.api.ChatColor;
import net.md_5.bungee.api.chat.TextComponent;
import net.md_5.bungee.api.connection.PendingConnection;
import net.md_5.bungee.api.event.LoginEvent;
import net.md_5.bungee.api.event.PlayerDisconnectEvent;
import net.md_5.bungee.api.event.PostLoginEvent;
import net.md_5.bungee.api.plugin.Listener;
import net.md_5.bungee.api.plugin.Plugin;
import net.md_5.bungee.event.EventHandler;

import java.net.InetSocketAddress;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Runs the NetworkAntiVPN pipeline on {@link LoginEvent}, the BungeeCord
 * equivalent of Velocity's {@code LoginEvent} - fires after authentication,
 * before the player is routed to a backend. Async handling uses
 * BungeeCord's {@code registerIntent}/{@code completeIntent} mechanism
 * since, unlike Velocity, Bungee's event system predates
 * {@code CompletableFuture}-based event results.
 */
public final class BungeeConnectionListener implements Listener {

    private final Plugin plugin;
    private final AntiVpnContext context;
    private final ConcurrentHashMap<UUID, String> pendingWarnings = new ConcurrentHashMap<>();

    public BungeeConnectionListener(Plugin plugin, AntiVpnContext context) {
        this.plugin = plugin;
        this.context = context;
    }

    @EventHandler
    public void onLogin(LoginEvent event) {
        if (!context.config().enabled()) {
            return;
        }
        event.registerIntent(plugin);

        PendingConnection connection = event.getConnection();
        InetSocketAddress address = connection.getAddress();
        String ip = address.getAddress().getHostAddress();

        ConnectionContext ctx = new ConnectionContext(connection.getUniqueId(), connection.getName(), ip,
                Platform.JAVA, null, null, ConnectionContext.Stage.PROXY_LOGIN, Instant.now());

        context.engine().analyzeConnection(ctx).whenComplete((decision, throwable) -> {
            if (throwable == null) {
                applyDecision(event, connection.getUniqueId(), decision);
            }
            event.completeIntent(plugin);
        });
    }

    private void applyDecision(LoginEvent event, UUID uuid, DecisionResult decision) {
        if (decision.isBlocked() || decision.decision() == Decision.REQUIRE_VERIFICATION) {
            event.setCancelled(true);
            event.setCancelReason(TextComponent.fromLegacyText(
                    ChatColor.translateAlternateColorCodes('&', context.messages().get(decision.messageKey()))));
        } else if (decision.decision() == Decision.ALLOW_WITH_WARNING) {
            // No ProxiedPlayer object exists yet at LoginEvent time; queue the
            // message and deliver it once the player fully connects.
            pendingWarnings.put(uuid, context.messages().get(decision.messageKey()));
        }
    }

    @EventHandler
    public void onPostLogin(PostLoginEvent event) {
        String message = pendingWarnings.remove(event.getPlayer().getUniqueId());
        if (message != null) {
            event.getPlayer().sendMessage(TextComponent.fromLegacyText(
                    ChatColor.translateAlternateColorCodes('&', message)));
        }
    }

    @EventHandler
    public void onDisconnect(PlayerDisconnectEvent event) {
        UUID uuid = event.getPlayer().getUniqueId();
        pendingWarnings.remove(uuid);
        context.engine().clearSessionResult(uuid);
    }
}
