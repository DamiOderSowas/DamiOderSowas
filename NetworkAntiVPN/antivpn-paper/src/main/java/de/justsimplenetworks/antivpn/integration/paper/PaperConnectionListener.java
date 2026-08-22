package de.justsimplenetworks.antivpn.integration.paper;

import de.justsimplenetworks.antivpn.api.ConnectionContext;
import de.justsimplenetworks.antivpn.api.Decision;
import de.justsimplenetworks.antivpn.api.DecisionResult;
import de.justsimplenetworks.antivpn.api.Platform;
import de.justsimplenetworks.antivpn.core.AntiVpnContext;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerPreLoginEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Runs on {@link AsyncPlayerPreLoginEvent}, which Bukkit explicitly fires
 * off the main server thread, making it safe to wait on the (already
 * asynchronous) NetworkAntiVPN pipeline here without stalling the server.
 * <p>
 * If {@code paper.reuse-proxy-result} is enabled and a Velocity-forwarded
 * result already exists for this player's session (see
 * {@link PaperResultListener}), it is reused instead of a fresh check.
 * Since Bukkit plugin messaging can only be delivered once a {@code Player}
 * object exists (which happens after this event), that forwarded result
 * typically only helps a second connection attempt within the same
 * session - see {@code docs/PAPER.md} for the exact timing tradeoffs. This
 * plugin is fully capable of running detection standalone otherwise.
 */
public final class PaperConnectionListener implements Listener {

    private static final LegacyComponentSerializer LEGACY = LegacyComponentSerializer.legacyAmpersand();

    private final AntiVpnContext context;
    private final ConcurrentHashMap<UUID, String> pendingWarnings = new ConcurrentHashMap<>();

    public PaperConnectionListener(AntiVpnContext context) {
        this.context = context;
    }

    @EventHandler
    public void onAsyncPreLogin(AsyncPlayerPreLoginEvent event) {
        if (!context.config().enabled()) {
            return;
        }

        UUID uuid = event.getUniqueId();
        String ip = event.getAddress().getHostAddress();

        DecisionResult decision;
        Optional<DecisionResult> forwarded = context.config().paperReuseProxyResult()
                ? context.engine().getSessionResult(uuid)
                : Optional.empty();

        if (forwarded.isPresent()) {
            decision = forwarded.get();
        } else {
            ConnectionContext ctx = new ConnectionContext(uuid, event.getName(), ip, Platform.JAVA,
                    null, null, ConnectionContext.Stage.BACKEND_JOIN, Instant.now());
            decision = context.engine().analyzeConnection(ctx).join();
        }

        applyDecision(event, decision, uuid);
    }

    private void applyDecision(AsyncPlayerPreLoginEvent event, DecisionResult decision, UUID uuid) {
        if (decision.isBlocked() || decision.decision() == Decision.REQUIRE_VERIFICATION) {
            Component message = LEGACY.deserialize(context.messages().get(decision.messageKey()));
            event.disallow(AsyncPlayerPreLoginEvent.Result.KICK_OTHER, message);
        } else if (decision.decision() == Decision.ALLOW_WITH_WARNING) {
            pendingWarnings.put(uuid, context.messages().get(decision.messageKey()));
        }
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        String message = pendingWarnings.remove(event.getPlayer().getUniqueId());
        if (message != null) {
            event.getPlayer().sendMessage(LEGACY.deserialize(message));
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        UUID uuid = event.getPlayer().getUniqueId();
        pendingWarnings.remove(uuid);
        context.engine().clearSessionResult(uuid);
    }
}
