package de.justsimplenetworks.antivpn.integration.velocity;

import com.google.inject.Inject;
import com.velocitypowered.api.command.CommandMeta;
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.proxy.ProxyInitializeEvent;
import com.velocitypowered.api.event.proxy.ProxyShutdownEvent;
import com.velocitypowered.api.plugin.Plugin;
import com.velocitypowered.api.plugin.annotation.DataDirectory;
import com.velocitypowered.api.proxy.ProxyServer;
import com.velocitypowered.api.proxy.messages.MinecraftChannelIdentifier;
import de.justsimplenetworks.antivpn.commands.AntiVpnCommandExecutor;
import de.justsimplenetworks.antivpn.core.AntiVpnBootstrap;
import de.justsimplenetworks.antivpn.core.AntiVpnContext;
import org.slf4j.Logger;

import java.nio.file.Path;

/**
 * Velocity is the primary NetworkAntiVPN platform. This plugin performs the
 * detection/decision pipeline as early as possible in the connection
 * lifecycle (during login, before a backend server is chosen) and, once a
 * player is routed to a backend, forwards the computed result over a plugin
 * messaging channel so Paper/Purpur never re-run detection for the same
 * session - see {@link VelocityConnectionListener} and
 * {@link ResultForwardingChannel}.
 */
@Plugin(
        id = "networkantivpn",
        name = "NetworkAntiVPN",
        version = "1.0.0-SNAPSHOT",
        description = "Standalone Anti-VPN/Anti-Proxy detection for JustSimpleNetworks Minecraft networks.",
        authors = {"JustSimpleNetworks"}
)
public final class VelocityAntiVpnPlugin {

    public static final MinecraftChannelIdentifier RESULT_CHANNEL =
            MinecraftChannelIdentifier.create("networkantivpn", "sync");

    private final ProxyServer proxyServer;
    private final Logger logger;
    private final Path dataDirectory;

    private AntiVpnContext context;

    @Inject
    public VelocityAntiVpnPlugin(ProxyServer proxyServer, Logger logger, @DataDirectory Path dataDirectory) {
        this.proxyServer = proxyServer;
        this.logger = logger;
        this.dataDirectory = dataDirectory;
    }

    @Subscribe
    public void onProxyInitialize(ProxyInitializeEvent event) {
        this.context = AntiVpnBootstrap.bootstrap(dataDirectory);

        VelocityPlayerLookup playerLookup = new VelocityPlayerLookup(proxyServer);
        AntiVpnCommandExecutor executor = new AntiVpnCommandExecutor(context, playerLookup);

        proxyServer.getEventManager().register(this, new VelocityConnectionListener(context));
        if (context.config().velocityForwardResultToBackend()) {
            proxyServer.getChannelRegistrar().register(RESULT_CHANNEL);
            proxyServer.getEventManager().register(this, new ResultForwardingChannel(context));
        }

        CommandMeta meta = proxyServer.getCommandManager().metaBuilder("antivpn")
                .aliases("networkantivpn", "avpn")
                .plugin(this)
                .build();
        proxyServer.getCommandManager().register(meta, new VelocityAntiVpnCommand(executor));

        logger.info("NetworkAntiVPN enabled with {} provider(s).", context.providers().size());
    }

    @Subscribe
    public void onProxyShutdown(ProxyShutdownEvent event) {
        if (context != null) {
            context.shutdown();
        }
    }
}
