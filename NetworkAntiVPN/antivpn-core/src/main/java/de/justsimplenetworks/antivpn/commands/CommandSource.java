package de.justsimplenetworks.antivpn.commands;

import java.util.Optional;
import java.util.UUID;

/**
 * Platform-agnostic abstraction over whoever ran a command (a player or the
 * console), implemented per platform (Velocity {@code CommandSource},
 * Bukkit {@code CommandSender}, BungeeCord {@code CommandSender}). Messages
 * passed to {@link #sendMessage(String)} use {@code &}-prefixed legacy color
 * codes; the adapter is responsible for translating them to whatever the
 * platform's chat API expects (e.g. Adventure {@code Component}s).
 */
public interface CommandSource {

    void sendMessage(String legacyColoredMessage);

    boolean hasPermission(String permission);

    String name();

    Optional<UUID> playerUuid();

    default boolean isPlayer() {
        return playerUuid().isPresent();
    }
}
