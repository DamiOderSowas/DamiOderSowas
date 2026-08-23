package de.justsimplenetworks.antivpn.integration.bungeecord;

import de.justsimplenetworks.antivpn.commands.AntiVpnCommandExecutor;
import de.justsimplenetworks.antivpn.core.AntiVpnBootstrap;
import de.justsimplenetworks.antivpn.core.AntiVpnContext;
import net.md_5.bungee.api.ProxyServer;
import net.md_5.bungee.api.plugin.Plugin;

/**
 * BungeeCord compatibility layer (secondary platform - Velocity is
 * primary). Mirrors {@code integration.velocity.VelocityAntiVpnPlugin} as
 * closely as BungeeCord's older, non-future-based event API allows: login
 * checks use BungeeCord's {@code registerIntent}/{@code completeIntent}
 * mechanism for async handling instead of Velocity's {@code EventTask}.
 * <p>
 * Logging: BungeeCord has no SLF4J binding of its own, so this module
 * shades in {@code slf4j-jdk14} (see its {@code pom.xml}), which routes
 * antivpn-core's plain SLF4J log calls into {@code java.util.logging} -
 * exactly what BungeeCord's own console logger already consumes.
 */
public final class BungeeAntiVpnPlugin extends Plugin {

    public static final String RESULT_CHANNEL = "networkantivpn:sync";

    private AntiVpnContext context;

    @Override
    public void onEnable() {
        this.context = AntiVpnBootstrap.bootstrap(getDataFolder().toPath());

        BungeePlayerLookup playerLookup = new BungeePlayerLookup();
        AntiVpnCommandExecutor executor = new AntiVpnCommandExecutor(context, playerLookup);

        ProxyServer proxy = getProxy();
        proxy.getPluginManager().registerListener(this, new BungeeConnectionListener(this, context));
        if (context.config().velocityForwardResultToBackend()) {
            proxy.registerChannel(RESULT_CHANNEL);
            proxy.getPluginManager().registerListener(this, new ResultForwardingListener(context));
        }
        proxy.getPluginManager().registerCommand(this, new BungeeAntiVpnCommand(executor));

        getLogger().info("NetworkAntiVPN enabled with " + context.providers().size() + " provider(s).");
    }

    @Override
    public void onDisable() {
        if (context != null) {
            context.shutdown();
        }
    }
}
