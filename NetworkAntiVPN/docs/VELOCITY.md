# Velocity Integration

Velocity is the **primary** platform. `antivpn-velocity` wires
`core.AntiVpnBootstrap` to Velocity's event system.

## Connection flow

```
Client -> Velocity -> NetworkAntiVPN -> Connection Analysis -> Decision -> Backend
```

`integration.velocity.VelocityConnectionListener` hooks Velocity's
`LoginEvent` - it fires after authentication (so a real player UUID is
known) but before the player is routed to any backend server. Handling is
asynchronous via `EventTask.resumeWhenComplete(...)`, so the pipeline's
provider I/O never blocks Velocity's event loop.

- `BLOCK` / `TEMPORARY_BLOCK` / `RATE_LIMITED` / `REQUIRE_VERIFICATION` ->
  `event.setResult(ComponentResult.denied(...))` with the message from
  `messages.yml` (via `decision.DecisionMessageKeys`).
- `ALLOW_WITH_WARNING` -> a chat message is sent to the player once logged in.
- `BYPASS` / `ALLOW` / `ALLOW_WITH_LOG` -> connection proceeds silently.

`DisconnectEvent` clears the stored session result for that player.

## Forwarding the result to the backend

If `velocity.forward-result-to-backend` is enabled,
`integration.velocity.ResultForwardingChannel` sends the computed
`DecisionResult` (encoded via `core.SessionResultPayload`, a small
hand-rolled binary format) over a `networkantivpn:sync` plugin messaging
channel when `ServerConnectedEvent` fires.

### Honest limitation: this cannot pre-empt the backend's own first check

Bukkit's plugin messaging API only delivers messages to/from a `Player`
object, which does not exist yet during `AsyncPlayerPreLoginEvent` on the
backend - so this forwarded message necessarily arrives *after* the backend
has already started (or even finished) its own login check for the
player's **first** connection to that backend in this session. Achieving a
race-free "the backend never even attempts its own check" would require
intercepting Velocity's raw login plugin message sequence at the protocol
level, which needs NMS/protocol hooking this project deliberately avoids
(see `general.debug` note in [SECURITY.md](SECURITY.md) and the "Kein
unnötiges NMS" principle in [ARCHITECTURE.md](ARCHITECTURE.md)).

What this forwarding *does* reliably provide:
- Data available to the backend for `/antivpn debug`/staff tooling and
  future `NetworkCoreBridge`/`NetworkAntiMultiAccount` consumption without
  a second provider round-trip.
- A real optimization whenever the backend's own local `DetectionCache`
  already holds a valid entry for the IP (e.g. the player reconnected, or
  another player from the same IP was already checked) - see
  [PAPER.md](PAPER.md) for the exact reuse order.

A fully cross-process, zero-redundant-check cache is a natural fit for a
future NetworkRedis-backed `DetectionCache` implementation - see
[INTEGRATION.md](INTEGRATION.md).

## Commands

`integration.velocity.VelocityAntiVpnCommand` registers `/antivpn` (aliases
`/networkantivpn`, `/avpn`) via Velocity's `SimpleCommand` API, delegating
to the shared `commands.AntiVpnCommandExecutor`. See [COMMANDS.md](COMMANDS.md).

## Metadata

Velocity plugins declare metadata via the `@Plugin` annotation on the main
class (`integration.velocity.VelocityAntiVpnPlugin`) - there is no separate
`velocity-plugin.json`/`plugin.yml` to ship.
