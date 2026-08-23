# BungeeCord Integration

BungeeCord is the **secondary** proxy platform - Velocity remains primary
and recommended. `antivpn-bungeecord` mirrors
`integration.velocity` as closely as BungeeCord's older, non-future-based
event API allows.

## Connection flow

`integration.bungeecord.BungeeConnectionListener` hooks BungeeCord's
`LoginEvent` (fires after authentication, before backend routing - the
Bungee equivalent of Velocity's `LoginEvent`). Since Bungee's event system
predates `CompletableFuture`-based results, async handling uses Bungee's
own `event.registerIntent(plugin)` / `event.completeIntent(plugin)`
mechanism: the event is held open until the NetworkAntiVPN pipeline
completes, then the intent is completed and Bungee resumes processing the
login.

- `BLOCK` / `TEMPORARY_BLOCK` / `RATE_LIMITED` / `REQUIRE_VERIFICATION` ->
  `event.setCancelled(true)` + `event.setCancelReason(...)` with the
  `messages.yml` message.
- `ALLOW_WITH_WARNING` -> queued and delivered on `PostLoginEvent`, since no
  `ProxiedPlayer` object (and therefore no way to send a chat message)
  exists yet during `LoginEvent`.
- `PlayerDisconnectEvent` clears the stored session result and any pending
  warning.

## Forwarding the result to the backend

`integration.bungeecord.ResultForwardingListener` sends the computed
result over the `networkantivpn:sync` channel on `ServerConnectedEvent`,
using the same `core.SessionResultPayload` wire format as the Velocity
module - see [VELOCITY.md](VELOCITY.md#forwarding-the-result-to-the-backend)
for the identical timing tradeoffs.

## Logging

Unlike modern Paper, BungeeCord has no SLF4J binding of its own (it logs
via `java.util.logging`). `antivpn-bungeecord` shades in `slf4j-jdk14`,
which routes `antivpn-core`'s plain SLF4J calls into `java.util.logging` -
exactly what Bungee's console logger already consumes.

## Commands

`integration.bungeecord.BungeeAntiVpnCommand` extends BungeeCord's
`Command`, delegating to the shared `commands.AntiVpnCommandExecutor`. See
[COMMANDS.md](COMMANDS.md).

## Metadata

Declared in `bungee.yml` (BungeeCord's plugin descriptor, analogous to
Bukkit's `plugin.yml`).
