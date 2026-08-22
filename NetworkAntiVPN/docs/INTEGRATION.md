# Integration

## Current state: fully standalone

NetworkCore does not exist yet. NetworkAntiVPN has **zero** hard dependency
on it, on any other JustSimpleNetworks project, or on a database/Redis. It
runs entirely on in-memory state plus small JSON files in its data folder
(`whitelist.json`, `blacklist.json`, `bypass.json`).

## The seam: `integration.networkcore.NetworkCoreBridge`

```java
public interface NetworkCoreBridge {
    boolean isAvailable();
    CompletableFuture<Void> publishRiskProfile(PlayerRiskProfile profile);
    CompletableFuture<UUID> resolveCanonicalIdentity(UUID playerUuid);
}
```

`integration.networkcore.NoopNetworkCoreBridge` is the default, always-safe
implementation used today (`isAvailable()` returns `false`; the other
methods are no-ops/identity functions). `core.AntiVpnBootstrap.bootstrap`
accepts an optional `NetworkCoreBridge` for a future real implementation to
plug in without touching any other class.

## What NetworkCore will eventually be able to plug into

Once it exists, a `NetworkCoreBridge` implementation is expected to bridge:

- **NetworkIdentity** - canonical player identity across the network.
- **NetworkPlayerData** / **NetworkSessions** / **NetworkPresence** - shared
  player/session state instead of this plugin's own in-memory session map.
- **NetworkPermissions** / **NetworkRanks** - permission resolution for
  `/antivpn` commands, replacing the platform-native permission checks.
- **NetworkServer** / **NetworkServerGroups** / **NetworkServerStatus** -
  richer server/server-group identity for the country filter's per-server
  rules than the plain string names used today.
- **NetworkRouting** / **NetworkQueue** / **NetworkProxy** / **NetworkTransfer** -
  a decision could influence routing directly instead of only allow/block.
- **NetworkSecurity** / **NetworkPunishments** / **NetworkAudit** - a
  `BLOCK` decision could feed a network-wide punishment/audit trail instead
  of (or in addition to) this plugin's own logs.
- **NetworkDatabase** / **NetworkCache** / **NetworkRedis** - a shared,
  cross-process detection cache (see the caveat in [VELOCITY.md](VELOCITY.md)
  about the current in-memory cache being per-JVM) and durable whitelist/
  blacklist/bypass storage instead of local JSON files.
- **NetworkConfiguration** / **NetworkSecrets** - centrally managed config
  and provider API keys instead of local `config.yml` + environment
  variables.
- **NetworkMetrics** / **NetworkLogging** / **NetworkHealth** - export
  `metrics.MetricsRegistry`'s counters and provider health into a
  network-wide observability system.
- **NetworkAntiMultiAccount** - the primary consumer of
  `api.PlayerRiskProfile` (UUID, IP hash, country, ASN, risk score,
  platform, timestamp) for enforcing per-IP account limits. NetworkAntiVPN
  deliberately does **not** implement multi-account limiting itself; see
  the "Anti-Multi-Account preparation" note below.

## Anti-multi-account preparation

NetworkAntiVPN is not, and will not become, the multi-account limiting
system. It only provides the data such a system needs:
`AntiVpnService.getRiskProfile(UUID)` returns an `api.PlayerRiskProfile`
with UUID, player name, optionally the raw IP (`privacy.store-raw-ip`),
always a salted IP hash, country, ASN, risk score, platform, and
connection timestamp. A future `NetworkAntiMultiAccount` project (or the
`NetworkCoreBridge`) is expected to consume this to enforce, for example,
max 5 Java accounts / max 5 Bedrock accounts / max 10 accounts total per IP.

## Other future JustSimpleNetworks integrations

The same principle applies to `NetworkIdentity`, `NetworkSessions`,
`NetworkSecurity`, `NetworkConnectionControl`, `NetworkBlacklist`,
`NetworkVerification`, `NetworkAudit`, `NetworkRedis`, `NetworkDatabase`,
`NetworkMetrics`, `NetworkLogging`, `NetworkDiscord`/`NetworkDiscordVerify`/
`NetworkDiscordRoleSync`: NetworkAntiVPN stays its own repository with its
own responsibility (VPN/proxy detection and connection decisions) and
exposes data/events (`api.event`, `api.AntiVpnService`,
`api.PlayerRiskProfile`) for those systems to consume, rather than
re-implementing any of their logic itself.

## Cross-plugin events today

Even without NetworkCore, any plugin running in the same JVM (a Paper
plugin alongside `antivpn-paper`, for example) can already subscribe to
`AntiVpnService.events()` (an `api.event.EventBus`) for
`ConnectionAnalysisStartedEvent`, `VPNDetectedEvent`, `ProxyDetectedEvent`,
`HostingDetectedEvent`, `TorDetectedEvent`, `HighRiskConnectionEvent`,
`ConnectionDecisionEvent`, `ConnectionBlockedEvent`, and
`ConnectionAllowedEvent` - see [API.md](API.md).
