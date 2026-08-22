package de.justsimplenetworks.antivpn.integration.paper;

import de.justsimplenetworks.antivpn.commands.AntiVpnCommandExecutor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;

import java.util.List;

public final class PaperAntiVpnCommand implements CommandExecutor, TabCompleter {

    private static final List<String> SUBCOMMANDS = List.of("check", "checkip", "inspect", "debug", "reload",
            "status", "providers", "cache", "whitelist", "blacklist", "bypass", "trust", "untrust");

    private final AntiVpnCommandExecutor executor;

    public PaperAntiVpnCommand(AntiVpnCommandExecutor executor) {
        this.executor = executor;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        executor.execute(new PaperCommandSource(sender), args);
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            return SUBCOMMANDS.stream().filter(s -> s.startsWith(args[0].toLowerCase())).toList();
        }
        return List.of();
    }
}
