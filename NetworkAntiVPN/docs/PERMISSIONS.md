# Permissions

| Node | Grants |
|---|---|
| `network.antivpn.admin` | Every permission below. |
| `network.antivpn.check` | `/antivpn check`, `/antivpn checkip` |
| `network.antivpn.inspect` | `/antivpn inspect` |
| `network.antivpn.debug` | `/antivpn debug` |
| `network.antivpn.reload` | `/antivpn reload` |
| `network.antivpn.status` | `/antivpn status` |
| `network.antivpn.providers` | `/antivpn providers` |
| `network.antivpn.cache` | `/antivpn cache`, `/antivpn cache clear` |
| `network.antivpn.whitelist` | `/antivpn whitelist add\|remove\|list` |
| `network.antivpn.blacklist` | `/antivpn blacklist add\|remove\|list` |
| `network.antivpn.bypass` | `/antivpn bypass ...` |
| `network.antivpn.trust` | `/antivpn trust`, `/antivpn untrust` |

All default to `op` on Paper/Purpur (see `plugin.yml`). Velocity and
BungeeCord have no built-in permission defaults - grant these via LuckPerms
or your permissions plugin of choice on those platforms.

`commands.AntiVpnCommandExecutor#requirePermission` checks
`network.antivpn.admin` first, then the specific node - a player with
`admin` never needs the individual nodes.
