package de.justsimplenetworks.antivpn.integration.bungeecord;

import de.justsimplenetworks.antivpn.commands.AntiVpnCommandExecutor;
import net.md_5.bungee.api.CommandSender;
import net.md_5.bungee.api.plugin.Command;

public final class BungeeAntiVpnCommand extends Command {

    private final AntiVpnCommandExecutor executor;

    public BungeeAntiVpnCommand(AntiVpnCommandExecutor executor) {
        super("antivpn", null, "networkantivpn", "avpn");
        this.executor = executor;
    }

    @Override
    public void execute(CommandSender sender, String[] args) {
        executor.execute(new BungeeCommandSource(sender), args);
    }
}
