# Commands

All commands are under `/antivpn` (aliases `/networkantivpn`, `/avpn`),
implemented once in `commands.AntiVpnCommandExecutor` and shared by every
platform module via the `commands.CommandSource`/`commands.PlayerLookup`
adapters.

| Command | Permission | Description |
|---|---|---|
| `/antivpn` | - | Shows help. |
| `/antivpn check <player>` | `network.antivpn.check` | Runs/shows the analysis for an **online** player: country, ASN/ISP, VPN/proxy/hosting/datacenter/Tor/residential-proxy flags, risk score, decision. |
| `/antivpn checkip <ip>` | `network.antivpn.check` | Same, for an arbitrary IPv4/IPv6 address (no player required). |
| `/antivpn inspect <player>` | `network.antivpn.inspect` | Like `check`, plus a per-provider breakdown (status, cached, latency). |
| `/antivpn debug <player>` | `network.antivpn.debug` | Same detail level as `inspect`, intended for staff troubleshooting. |
| `/antivpn reload` | `network.antivpn.reload` | Reloads `config.yml`/`messages.yml` and re-applies whitelist/blacklist/country/risk/decision config. |
| `/antivpn status` | `network.antivpn.status` | Shows every metric counter and average provider latency. |
| `/antivpn providers` | `network.antivpn.providers` | Lists every provider's enabled state, health, average latency, error count. |
| `/antivpn cache` | `network.antivpn.cache` | Shows cache size/hits/misses/hit rate. |
| `/antivpn cache clear` | `network.antivpn.cache` | Clears the detection cache. |
| `/antivpn whitelist add <player\|ip\|cidr> [duration]` | `network.antivpn.whitelist` | Adds a whitelist entry, permanent unless a duration is given. |
| `/antivpn whitelist remove <player\|ip\|cidr>` | `network.antivpn.whitelist` | Removes a runtime whitelist entry. |
| `/antivpn whitelist list` | `network.antivpn.whitelist` | Lists all whitelist entries (config + runtime). |
| `/antivpn blacklist add <player\|ip\|cidr\|asn> [duration]` | `network.antivpn.blacklist` | Adds a blacklist entry. |
| `/antivpn blacklist remove <player\|ip\|cidr\|asn>` | `network.antivpn.blacklist` | Removes a runtime blacklist entry. |
| `/antivpn blacklist list` | `network.antivpn.blacklist` | Lists all blacklist entries. |
| `/antivpn bypass <player> <duration>` | `network.antivpn.bypass` | Grants a temporary bypass. |
| `/antivpn bypass remove <player>` | `network.antivpn.bypass` | Revokes an active bypass. |
| `/antivpn bypass list` | `network.antivpn.bypass` | Lists active bypasses. |
| `/antivpn trust <player>` | `network.antivpn.trust` | False-positive correction: permanently whitelists the player. |
| `/antivpn untrust <player>` | `network.antivpn.trust` | Removes that whitelist entry. |

`network.antivpn.admin` grants every permission above.

## Duration format

See [BYPASS.md#duration-format](BYPASS.md#duration-format) - `30s`, `10m`,
`1h`, `1d`, `7d`, and combinations like `1d12h`.

## Player/IP/CIDR/ASN argument disambiguation

For `whitelist`/`blacklist add|remove`, the target argument is classified
automatically: a literal IP -> IP entry; a value containing `/` -> CIDR
entry; a value that is all digits -> ASN entry (blacklist only); otherwise
it is resolved as a player name/UUID against currently online players (see
[PlayerLookup] below).

## Player resolution

`commands.PlayerLookup` only resolves **online** players (by name or UUID) -
this project deliberately does not perform its own Mojang API lookups for
offline players (see [SECURITY.md](SECURITY.md)). Passing a raw UUID always
works, online or not, since blacklist/whitelist/bypass entries are stored
by UUID.

## Sensitive output

`/antivpn check`/`inspect`/`debug` output (IP, ASN, ISP, provider details)
is gated by permission and is not shown to normal players by any command in
this project.
