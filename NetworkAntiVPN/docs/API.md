# Public API

`de.justsimplenetworks.antivpn.api` (and `api.event`) is the only package
other JustSimpleNetworks plugins - and any code in the same JVM - should
depend on. Everything else (`core`, `detection`, `providers`, ...) is an
implementation detail that may change between releases.

## `AntiVpnService`

Implemented by `core.AntiVpnEngine`; obtained from
`core.AntiVpnContext#engine()` after `core.AntiVpnBootstrap.bootstrap(...)`.

```java
CompletableFuture<DecisionResult> analyzeConnection(ConnectionContext context);
Optional<DecisionResult> getSessionResult(UUID playerUuid);
void storeSessionResult(UUID playerUuid, DecisionResult result);
void clearSessionResult(UUID playerUuid);
Optional<PlayerRiskProfile> getRiskProfile(UUID playerUuid);
EventBus events();
void reload();
void shutdown();
```

## Core data types

- `DetectionResult` - one provider's answer for one IP.
- `AggregatedDetectionResult` - the merged verdict across all providers.
- `DecisionResult` - the final decision, reason, risk score/level, and the
  aggregated result it was based on (`null` for a whitelist/blacklist/
  bypass/rate-limit short-circuit).
- `ConnectionContext` - everything known about a connection attempt when a
  decision needs to be made (player UUID/name, IP, platform, server/server
  group, pipeline stage, timestamp).
- `PlayerRiskProfile` - the privacy-conscious summary exposed for other
  systems (see [PRIVACY.md](PRIVACY.md) and [INTEGRATION.md](INTEGRATION.md)).
- `Decision`, `DecisionReason`, `RiskLevel`, `Platform` - enums.

## Events (`api.event`)

`EventBus` (obtained via `AntiVpnService#events()`) is a minimal,
dependency-free publish/subscribe bus - not tied to any platform's native
event system:

```java
service.events().subscribe(VPNDetectedEvent.class, event -> {
    // event.context(), event.result()
});
```

Published events: `ConnectionAnalysisStartedEvent`,
`ConnectionAnalysisCompletedEvent`, `VPNDetectedEvent`, `ProxyDetectedEvent`,
`HostingDetectedEvent`, `TorDetectedEvent`, `HighRiskConnectionEvent`,
`ConnectionDecisionEvent`, `ConnectionBlockedEvent`, `ConnectionAllowedEvent`.

A listener exception is caught and logged - it can never break the
detection pipeline that published the event.

## Bridging onto a platform's native event system

Nothing in `antivpn-core` requires this, but a platform integration may
choose to re-fire these as native events (e.g. custom Bukkit events) so
plugins that don't want to depend on `antivpn-core` directly can still
listen. None of the bundled platform modules currently do this by default;
it is a natural extension point for a network-specific fork.

## Stability

Everything under `api` is intended to stay source-compatible across
releases - see the `AntiVpnService` Javadoc. Packages outside `api` may
change without notice between versions.
