# Whitelist

`whitelist.WhitelistService` is checked before any block decision - a match
short-circuits the entire pipeline with `Decision.BYPASS` before a single
detection provider is queried.

## Supported entry types

- **IP** - literal IPv4 or IPv6 (e.g. `127.0.0.1`, `2001:db8::1`).
- **CIDR** - IPv4 or IPv6 block (e.g. `192.168.0.0/16`, `2001:db8::/32`).
- **Player** - UUID (dashed or dashless) or, for entries loaded from
  `config.yml` only, a plain name (resolved against the connecting player's
  UUID/username at check time - no Mojang API lookup is performed).

## Static configuration (`config.yml`)

```yaml
whitelist:
  ips:
    - "127.0.0.1"
  cidrs:
    - "192.168.0.0/16"
  players:
    - "notch"
  uuids:
    - "069a79f4-44e9-4726-a5be-fca90e38aaf5"
```

Reloaded wholesale on `/antivpn reload` (or restart) - config-sourced
entries are fully replaced by whatever is currently in the file; entries
added via commands are untouched.

## Runtime commands

```
/antivpn whitelist add <player|ip|cidr> [duration]
/antivpn whitelist remove <player|ip|cidr>
/antivpn whitelist list
```

Omitting `[duration]` creates a **permanent** entry; a duration
(`30s`/`10m`/`1h`/`1d`/`7d`, see [BYPASS.md](BYPASS.md#duration-format))
creates a temporary one that is automatically pruned on expiry.

Runtime entries persist to `<data>/whitelist.json` (atomic writes) and
survive a restart.

## False-positive correction

`/antivpn trust <player>` is equivalent to a permanent
`/antivpn whitelist add <player>` with an audit reason of "false-positive
correction"; `/antivpn untrust <player>` removes it. See
[COMMANDS.md](COMMANDS.md).

## Priority

Whitelist is checked **before** the blacklist and before detection. A
player/IP that is both whitelisted and blacklisted is allowed - whitelist
always wins.
