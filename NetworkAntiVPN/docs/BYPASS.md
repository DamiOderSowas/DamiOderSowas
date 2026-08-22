# Temporary Bypass

`bypass.BypassService` grants a player a time-limited exemption from every
further check (whitelist/blacklist/detection/country/rate-limit all become
irrelevant while a bypass is active - it is checked second in the pipeline,
right after rate limiting).

## Command

```
/antivpn bypass <player> <duration>
/antivpn bypass remove <player>
/antivpn bypass list
```

## Duration format

Short, composable duration strings, parsed by `util.DurationUtils`:

| Unit | Example |
|---|---|
| seconds | `30s` |
| minutes | `10m` |
| hours | `1h` |
| days | `1d`, `7d` |
| weeks | `2w` |

Segments can be combined: `1d12h30m`.

## What is stored

Per bypass (`bypass.BypassEntry`): player UUID, the IP the bypass was
granted for (audit-only - the bypass is keyed by UUID, not IP), reason,
granting staff member (or "console"), creation time, and expiry.

Persisted to `<data>/bypass.json`; a background task removes expired
entries automatically (checked every 30 seconds, and lazily on every
`isActive()` check).

## Relationship to temporary whitelist

A temporary bypass (`/antivpn bypass`) and a temporary whitelist entry
(`/antivpn whitelist add <player> <duration>`) are functionally similar
(both time-limited, both grant BYPASS) but are tracked as separate concepts
with separate audit trails and commands, matching the specification. Use
bypass for "let this specific player through right now regardless of
anything" and whitelist for "this player/IP/CIDR is trusted."
