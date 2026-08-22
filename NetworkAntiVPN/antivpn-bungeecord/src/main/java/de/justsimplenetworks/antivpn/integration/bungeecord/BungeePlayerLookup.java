package de.justsimplenetworks.antivpn.integration.bungeecord;

import de.justsimplenetworks.antivpn.commands.PlayerLookup;
import de.justsimplenetworks.antivpn.util.UuidUtils;
import net.md_5.bungee.api.ProxyServer;
import net.md_5.bungee.api.connection.ProxiedPlayer;

import java.util.Optional;
import java.util.UUID;

public final class BungeePlayerLookup implements PlayerLookup {

    @Override
    public Optional<PlayerInfo> findOnlinePlayer(String nameOrUuid) {
        UUID uuid = UuidUtils.parseOrNull(nameOrUuid);
        ProxiedPlayer player = uuid != null
                ? ProxyServer.getInstance().getPlayer(uuid)
                : ProxyServer.getInstance().getPlayer(nameOrUuid);
        if (player == null) {
            return Optional.empty();
        }
        return Optional.of(new PlayerInfo(player.getUniqueId(), player.getName(),
                player.getSocketAddress() instanceof java.net.InetSocketAddress addr
                        ? addr.getAddress().getHostAddress() : "unknown"));
    }
}
