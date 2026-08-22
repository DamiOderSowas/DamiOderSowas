package de.justsimplenetworks.antivpn.integration.velocity;

import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.ProxyServer;
import de.justsimplenetworks.antivpn.commands.PlayerLookup;
import de.justsimplenetworks.antivpn.util.UuidUtils;

import java.util.Optional;
import java.util.UUID;

public final class VelocityPlayerLookup implements PlayerLookup {

    private final ProxyServer proxyServer;

    public VelocityPlayerLookup(ProxyServer proxyServer) {
        this.proxyServer = proxyServer;
    }

    @Override
    public Optional<PlayerInfo> findOnlinePlayer(String nameOrUuid) {
        Optional<Player> player;
        UUID uuid = UuidUtils.parseOrNull(nameOrUuid);
        player = uuid != null ? proxyServer.getPlayer(uuid) : proxyServer.getPlayer(nameOrUuid);
        return player.map(p -> new PlayerInfo(p.getUniqueId(), p.getUsername(),
                p.getRemoteAddress().getAddress().getHostAddress()));
    }
}
