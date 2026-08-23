# Blacklist

`blacklist.BlacklistService` has high priority: IP/CIDR/player entries are
checked immediately after whitelist and before any provider is queried; ASN
entries are checked right after an ASN is resolved by detection (before
country filtering or risk scoring).

## Supported entry types

- **IP** / **CIDR** - same rules as [whitelist](WHITELIST.md).
- **Player** - UUID.
- **ASN** - autonomous system number (integer), checked once detection has
  resolved one for the connection.

## Static configuration (`config.yml`)

```yaml
blacklist:
  ips:
    - "1.2.3.4"
  cidrs:
    - "1.2.0.0/16"
  players: []
  uuids: []
  asns:
    - 12345
```

## Runtime commands

```
/antivpn blacklist add <player|ip|cidr|asn> [duration]
/antivpn blacklist remove <player|ip|cidr|asn>
/antivpn blacklist list
```

Same permanent/temporary semantics as the whitelist (see
[BYPASS.md#duration-format](BYPASS.md#duration-format)); persisted to
`<data>/blacklist.json`.

## Decision outcome

A permanent blacklist match produces `Decision.BLOCK`; a temporary one
produces `Decision.TEMPORARY_BLOCK` with `blockedUntil` set to the entry's
expiry. Reason is `BLACKLIST_IP`/`BLACKLIST_CIDR`/`BLACKLIST_PLAYER`/
`BLACKLIST_ASN`, or `TEMPORARY_BLACKLIST` for a temporary match.

## Priority

Blacklist is checked after whitelist/bypass (which always win) and before
detection runs for IP/CIDR/player matches - a blacklisted IP is blocked
without ever contacting a provider. ASN blacklist necessarily runs after
detection, since the ASN itself comes from a provider.
