package de.justsimplenetworks.antivpn.commands;

import java.util.Optional;
import java.util.UUID;

/**
 * Resolves a name/UUID argument to a currently-connected player, implemented
 * per platform. Commands only ever need to reach online players directly -
 * offline lookups intentionally are not supported here to avoid this module
 * making its own Mojang API calls (see {@code docs/SECURITY.md}).
 */
public interface PlayerLookup {

    Optional<PlayerInfo> findOnlinePlayer(String nameOrUuid);

    record PlayerInfo(UUID uuid, String name, String ip) {
    }
}
