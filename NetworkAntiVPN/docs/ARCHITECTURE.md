# Architecture

## Modules

```
antivpn-parent (pom.xml, no code)
├── antivpn-core       platform-independent engine (all logic lives here)
├── antivpn-velocity   primary proxy integration
├── antivpn-paper      backend integration (also works standalone)
├── antivpn-purpur     thin Paper subclass + Purpur-branded metadata
└── antivpn-bungeecord secondary/compatibility proxy integration
```

`antivpn-core` has **no** dependency on any Minecraft platform API. It only
depends on SLF4J, SnakeYAML and Gson, and the JDK's own `java.net.http`
client. This is what makes it independently unit-testable and lets every
platform module reuse the exact same pipeline via `core.AntiVpnBootstrap`.

## Packages (all under `de.justsimplenetworks.antivpn`)

| Package | Responsibility |
|---|---|
| `api` | Public data types (`DetectionResult`, `AggregatedDetectionResult`, `DecisionResult`, `ConnectionContext`, ...) and the `AntiVpnService` facade + event bus (`api.event`). |
| `core` | `AntiVpnEngine` (orchestrator), `AntiVpnBootstrap` (wiring), `AntiVpnContext` (bundle handed to integrations/commands), `SessionResultPayload` (proxy→backend wire format). |
| `detection` | `DetectionAggregator` - queries providers in parallel and merges results. |
| `providers` | `DetectionProvider` contract, `MockDetectionProvider`, `ConfigurableHttpDetectionProvider`, health tracking. |
| `cache` | `TtlCache`, `InMemoryDetectionCache`, `InMemoryProviderResultCache`. |
| `scoring` | `RiskScoringEngine`, configurable `RiskWeights`. |
| `decision` | `DecisionEngine`, configurable `DecisionRuleSet`, `FallbackPolicy`. |
| `whitelist` / `blacklist` | IP/CIDR/player(/ASN) lists, permanent + temporary, persisted to JSON. |
| `bypass` | Temporary per-player bypasses. |
| `country` | EU-only/Europe-only/allow-blocklist country & continent filtering. |
| `ratelimit` | Sliding-window connection and provider-request rate limiting. |
| `commands` | Platform-agnostic `/antivpn` command dispatcher (`CommandSource`/`PlayerLookup` abstractions). |
| `config` | YAML loading (`YamlConfig`, `AntiVpnConfig`, `MessagesConfig`). |
| `logging` | `SafeLogger` (log-injection-safe SLF4J wrapper). |
| `metrics` | In-process counters. |
| `security` | SSRF guard, response size limiting, log sanitization, IP hashing. |
| `util` | IP/CIDR parsing, duration parsing, JSON path resolution, JSON persistence. |
| `integration.networkcore` | `NetworkCoreBridge` seam for a future NetworkCore. |
| `integration.velocity` / `paper` / `purpur` / `bungeecord` | Platform glue only - no detection logic. |

## The pipeline

`core.AntiVpnEngine#analyzeConnection` runs, in order:

1. **Rate limit** (`ratelimit.RateLimiterService`) - per-IP and per-player
   sliding-window limits. Exceeding either short-circuits with `RATE_LIMITED`.
2. **Bypass** (`bypass.BypassService`) - an active temporary bypass
   short-circuits with `BYPASS`.
3. **Whitelist** (`whitelist.WhitelistService`) - IP/CIDR/player match
   short-circuits with `BYPASS`.
4. **Blacklist, IP/CIDR/player** (`blacklist.BlacklistService`) -
   short-circuits with `BLOCK`/`TEMPORARY_BLOCK`.
5. **Cache + detection** - a cached `AggregatedDetectionResult` is reused;
   otherwise `detection.DetectionAggregator` queries every enabled provider
   in parallel (see [DETECTION.md](DETECTION.md)) and the result is cached.
6. **Blacklist, ASN** - now that an ASN is known, checked against
   `blacklist.asns`.
7. **Country filter** (`country.CountryFilterService`) - EU-only/allow-/
   blocklist. A block here short-circuits.
8. **Risk scoring** (`scoring.RiskScoringEngine`) - produces a 0-100 score
   and `RiskLevel`.
9. **Decision engine** (`decision.DecisionEngine`) - evaluates the
   configured rule table against the aggregated result and risk level.

Every step publishes events on the internal `api.event.EventBus`
(`ConnectionAnalysisStartedEvent`, `VPNDetectedEvent`, `ConnectionDecisionEvent`,
`ConnectionBlockedEvent`/`ConnectionAllowedEvent`, ...), and the final
decision is stored for the session (`AntiVpnEngine#storeSessionResult`) so a
backend server does not have to redo the check - see
[VELOCITY.md](VELOCITY.md) and [PAPER.md](PAPER.md) for exactly how that
handoff works and its limitations.

## Constructor injection, not statics

Every service is constructed once in `core.AntiVpnBootstrap` and passed by
constructor into whatever needs it (`AntiVpnEngine`, `DetectionAggregator`,
`AntiVpnCommandExecutor`, ...). Nothing reaches for a global/static
singleton. This is what keeps `antivpn-core` unit-testable without a running
Minecraft server or plugin container - see the tests under
`antivpn-core/src/test/java`.
