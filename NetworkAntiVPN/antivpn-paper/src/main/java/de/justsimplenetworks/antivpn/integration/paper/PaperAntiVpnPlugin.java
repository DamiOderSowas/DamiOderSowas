package de.justsimplenetworks.antivpn.integration.paper;

import de.justsimplenetworks.antivpn.commands.AntiVpnCommandExecutor;
import de.justsimplenetworks.antivpn.core.AntiVpnContext;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Paper backend integration. Not {@code final} so {@code antivpn-purpur} can
 * extend this class directly - Purpur's API is a strict superset of
 * Paper's, so no Purpur-specific detection logic is needed, only different
 * plugin metadata (see {@code antivpn-purpur}).
 * <p>
 * Logging: antivpn-core logs through the plain SLF4J API
 * ({@code LoggerFactory.getLogger(...)}). Modern Paper already provides a
 * working SLF4J binding backed by its own Log4j2 core on the server
 * classpath, so those calls are routed to the console automatically -
 * nothing needs to be bridged or shaded here.
 */
public class PaperAntiVpnPlugin extends JavaPlugin {

    private AntiVpnContext context;

    @Override
    public void onEnable() {
        this.context = de.justsimplenetworks.antivpn.core.AntiVpnBootstrap.bootstrap(getDataFolder().toPath());

        PaperPlayerLookup playerLookup = new PaperPlayerLookup();
        AntiVpnCommandExecutor executor = new AntiVpnCommandExecutor(context, playerLookup);

        PluginManager pluginManager = getServer().getPluginManager();
        pluginManager.registerEvents(new PaperConnectionListener(context), this);

        if (context.config().paperReuseProxyResult()) {
            getServer().getMessenger().registerIncomingPluginChannel(this,
                    "networkantivpn:sync", new PaperResultListener(context));
        }

        PaperAntiVpnCommand command = new PaperAntiVpnCommand(executor);
        var pluginCommand = getCommand("antivpn");
        if (pluginCommand != null) {
            pluginCommand.setExecutor(command);
            pluginCommand.setTabCompleter(command);
        }

        getSLF4JLogger().info("NetworkAntiVPN enabled with {} provider(s).", context.providers().size());
    }

    @Override
    public void onDisable() {
        if (context != null) {
            context.shutdown();
        }
    }

    public AntiVpnContext context() {
        return context;
    }
}
