package de.justsimplenetworks.antivpn.integration.paper;

import de.justsimplenetworks.antivpn.commands.PlayerLookup;
import de.justsimplenetworks.antivpn.util.UuidUtils;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.Optional;
import java.util.UUID;

public final class PaperPlayerLookup implements PlayerLookup {

    @Override
    public Optional<PlayerInfo> findOnlinePlayer(String nameOrUuid) {
        UUID uuid = UuidUtils.parseOrNull(nameOrUuid);
        Player player = uuid != null ? Bukkit.getPlayer(uuid) : Bukkit.getPlayerExact(nameOrUuid);
        if (player == null || player.getAddress() == null) {
            return Optional.empty();
        }
        return Optional.of(new PlayerInfo(player.getUniqueId(), player.getName(),
                player.getAddress().getAddress().getHostAddress()));
    }
}
