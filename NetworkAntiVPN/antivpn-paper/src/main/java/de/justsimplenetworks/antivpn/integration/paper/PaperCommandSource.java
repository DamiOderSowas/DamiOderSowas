package de.justsimplenetworks.antivpn.integration.paper;

import de.justsimplenetworks.antivpn.commands.CommandSource;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.Optional;
import java.util.UUID;

public final class PaperCommandSource implements CommandSource {

    private static final LegacyComponentSerializer LEGACY = LegacyComponentSerializer.legacyAmpersand();

    private final CommandSender delegate;

    public PaperCommandSource(CommandSender delegate) {
        this.delegate = delegate;
    }

    @Override
    public void sendMessage(String legacyColoredMessage) {
        delegate.sendMessage(LEGACY.deserialize(legacyColoredMessage));
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
        return delegate instanceof Player player ? Optional.of(player.getUniqueId()) : Optional.empty();
    }
}
