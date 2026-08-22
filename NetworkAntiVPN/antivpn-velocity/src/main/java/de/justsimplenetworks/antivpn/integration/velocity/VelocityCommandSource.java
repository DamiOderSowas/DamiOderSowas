package de.justsimplenetworks.antivpn.integration.velocity;

import com.velocitypowered.api.proxy.Player;
import de.justsimplenetworks.antivpn.commands.CommandSource;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;

import java.util.Optional;
import java.util.UUID;

/** Adapts Velocity's {@code com.velocitypowered.api.command.CommandSource} to our platform-agnostic one. */
public final class VelocityCommandSource implements CommandSource {

    private static final LegacyComponentSerializer LEGACY = LegacyComponentSerializer.legacyAmpersand();

    private final com.velocitypowered.api.command.CommandSource delegate;

    public VelocityCommandSource(com.velocitypowered.api.command.CommandSource delegate) {
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
        return delegate instanceof Player player ? player.getUsername() : "Console";
    }

    @Override
    public Optional<UUID> playerUuid() {
        return delegate instanceof Player player ? Optional.of(player.getUniqueId()) : Optional.empty();
    }
}
