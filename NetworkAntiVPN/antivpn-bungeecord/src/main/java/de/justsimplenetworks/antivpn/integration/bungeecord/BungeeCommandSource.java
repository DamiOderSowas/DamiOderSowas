package de.justsimplenetworks.antivpn.integration.bungeecord;

import de.justsimplenetworks.antivpn.commands.CommandSource;
import net.md_5.bungee.api.ChatColor;
import net.md_5.bungee.api.chat.TextComponent;
import net.md_5.bungee.api.connection.ProxiedPlayer;

import java.util.Optional;
import java.util.UUID;

public final class BungeeCommandSource implements CommandSource {

    private final net.md_5.bungee.api.CommandSender delegate;

    public BungeeCommandSource(net.md_5.bungee.api.CommandSender delegate) {
        this.delegate = delegate;
    }

    @Override
    public void sendMessage(String legacyColoredMessage) {
        delegate.sendMessage(TextComponent.fromLegacyText(ChatColor.translateAlternateColorCodes('&', legacyColoredMessage)));
    }

    @Override
    public boolean hasPermission(String permission) {
        return delegate.hasPermission(permission);
    }

    @Override
    public String name() {
        return delegate.getName();
    }

    @Override
    public Optional<UUID> playerUuid() {
        return delegate instanceof ProxiedPlayer player ? Optional.of(player.getUniqueId()) : Optional.empty();
    }
}
