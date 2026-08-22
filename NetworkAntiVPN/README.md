# NetworkAntiVPN

Standalone Anti-VPN / Anti-Proxy detection system for **JustSimpleNetworks**
Minecraft networks.

NetworkAntiVPN detects VPNs, proxies, hosting/datacenter ranges, Tor exit
nodes and residential proxies on connection, resolves country/ASN/ISP data,
computes a 0-100 risk score, and applies a fully configurable decision
(allow, warn, require verification, temporary block, block) - all before a
player ever reaches your game world. It ships as five independent Maven/Gradle
modules and works standalone today; it is designed to plug into
[NetworkCore](https://github.com/JustSimpleNetworks) later without a rewrite.

> **Status:** NetworkCore does not exist yet. NetworkAntiVPN has **zero**
> hard dependency on it and is fully self-contained. See
> [docs/INTEGRATION.md](docs/INTEGRATION.md) for the planned adapter.

## What is NetworkAntiVPN?

It is the network's connection gatekeeper: a pipeline that turns a raw IP
address into an auditable decision.

```
Client -> Velocity -> NetworkAntiVPN -> Connection Analysis -> Decision -> Backend
```

The pipeline runs in this order (see [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md)):

```
rate limit -> bypass -> whitelist -> blacklist (ip/cidr/player)
  -> cache/detection -> blacklist (asn) -> country filter
  -> risk scoring -> decision engine
```

Whitelist, bypass, and blacklist can each short-circuit the whole pipeline -
matching the specification's priority rules - before a single provider is
ever queried.

## Which platforms are supported?

| Module               | Platform    | Role                                    |
|-----------------------|-------------|------------------------------------------|
| `antivpn-core`         | none        | Platform-independent engine (this is where all the logic lives) |
| `antivpn-velocity`      | Velocity    | **Primary.** Checks as early as possible during login. |
| `antivpn-paper`         | Paper       | Backend integration; standalone-capable. |
| `antivpn-purpur`        | Purpur      | Thin Paper subclass (Purpur's API is a Paper superset). |
| `antivpn-bungeecord`    | BungeeCord  | Secondary/compatibility proxy layer.     |

Focus Minecraft version: **1.21.11**. Supported range: 1.21.x-1.26.x.
Java 25. Maven is the primary build; Gradle is fully supported too (see
[BUILD](#build)).

## How does detection work?

Every configured [`DetectionProvider`](docs/PROVIDERS.md) is queried in
parallel for an IP (never blocking the Minecraft join thread - see
[docs/DETECTION.md](docs/DETECTION.md)). Each provider reports only what it
actually observed (a `DetectionResult` with nullable flags - no provider is
allowed to guess). A `DetectionAggregator` merges every provider's answer
into one verdict using confidence- and weight-aware majority voting, which
then feeds the [risk scoring engine](docs/RISK-SCORING.md) and the
[decision engine](docs/DECISIONS.md).

No vendor API is hard-coded: NetworkAntiVPN ships a generic, config-driven
`ConfigurableHttpDetectionProvider` (point `url-template` at whatever
IP-intelligence contract you have) plus a network-free `MockDetectionProvider`
for testing/demo wiring. See [docs/PROVIDERS.md](docs/PROVIDERS.md).

## How does EU-only work?

`countries.eu-only: true` in `config.yml` allows only the 27 EU member
states; `countries.europe-only: true` widens that to the whole European
continent (EU + UK, Switzerland, Norway, Iceland, Balkan states, ...). The
exact country lists are documented and versioned in
[docs/COUNTRY-FILTER.md](docs/COUNTRY-FILTER.md). Country data always comes
from IP geolocation via the configured providers - never from reverse DNS.
Per-server and per-server-group overrides are supported.

## How do whitelist and blacklist work?

Both support literal IPv4/IPv6, CIDR blocks, and player UUIDs (blacklist
additionally supports ASNs), each either permanent (from `config.yml`) or
temporary (`/antivpn whitelist add <target> <duration>`, persisted to
`whitelist.json`/`blacklist.json` in the data folder and pruned
automatically on expiry). Whitelist always wins; blacklist is checked before
any detection runs. See [docs/WHITELIST.md](docs/WHITELIST.md) and
[docs/BLACKLIST.md](docs/BLACKLIST.md).

## How do temporary bypasses work?

`/antivpn bypass <player> <duration>` (e.g. `30s`, `10m`, `1h`, `1d`, `7d`)
grants a player, IP, reason, staff member and expiry - stored and pruned the
same way as temporary whitelist/blacklist entries. See
[docs/BYPASS.md](docs/BYPASS.md).

## How does multi-provider work?

Providers run concurrently, each on its own timeout, each excluded from the
vote (not the whole check) on failure or timeout. Health
(online/degraded/rate-limited/offline), latency and error counts are
tracked per provider and visible via `/antivpn providers`. See
[docs/PROVIDERS.md](docs/PROVIDERS.md).

## How does the cache work?

An IP-keyed, TTL-based, size-bounded in-memory cache avoids a provider
request on every join once a result is cached; a second, provider-specific
cache avoids re-querying an individual provider even across a full
re-aggregation. Velocity additionally forwards its computed result to the
backend server over a plugin messaging channel. See
[docs/../docs/ARCHITECTURE.md](docs/ARCHITECTURE.md) and
[docs/VELOCITY.md](docs/VELOCITY.md)/[docs/PAPER.md](docs/PAPER.md) for the
exact cross-process reuse behaviour and its honest limitations.

## How does the risk score work?

A configurable 0-100 score (`risk-scoring.weights` in `config.yml`) banded
into SAFE/LOW/MEDIUM/HIGH/CRITICAL. See
[docs/RISK-SCORING.md](docs/RISK-SCORING.md).

## How does fallback work?

If every provider fails, `fallback.policy` decides what happens:
`FAIL_OPEN` (default - allow, logged, so an outage never locks out the whole
network), `FAIL_CLOSED` (block until a provider is reachable), or
`REQUIRE_VERIFICATION`. See [docs/PROVIDERS.md](docs/PROVIDERS.md#fallback).

## How will NetworkCore be integrated later?

Through `integration.networkcore.NetworkCoreBridge` - a single interface
with a no-op default implementation today. See
[docs/INTEGRATION.md](docs/INTEGRATION.md).

## Documentation

- [ARCHITECTURE](docs/ARCHITECTURE.md) - module layout and pipeline
- [CONFIGURATION](docs/CONFIGURATION.md) - full `config.yml` reference
- [PROVIDERS](docs/PROVIDERS.md) - writing/configuring detection providers
- [DETECTION](docs/DETECTION.md) - how signals are collected and merged
- [RISK-SCORING](docs/RISK-SCORING.md)
- [DECISIONS](docs/DECISIONS.md)
- [WHITELIST](docs/WHITELIST.md) / [BLACKLIST](docs/BLACKLIST.md) / [BYPASS](docs/BYPASS.md)
- [COUNTRY-FILTER](docs/COUNTRY-FILTER.md)
- [RATE-LIMIT](docs/RATE-LIMIT.md)
- [INTEGRATION](docs/INTEGRATION.md) - NetworkCore and other JustSimpleNetworks systems
- [VELOCITY](docs/VELOCITY.md) / [PAPER](docs/PAPER.md) / [PURPUR](docs/PURPUR.md) / [BUNGEECORD](docs/BUNGEECORD.md)
- [COMMANDS](docs/COMMANDS.md) / [PERMISSIONS](docs/PERMISSIONS.md)
- [SECURITY](docs/SECURITY.md) / [PRIVACY](docs/PRIVACY.md)
- [API](docs/API.md) - the public `api` package and event bus
- [TROUBLESHOOTING](docs/TROUBLESHOOTING.md)

## Build

Requires Java 25.

```bash
mvn clean verify   # Maven (primary)
gradle build        # Gradle, or ./gradlew build once the wrapper is present
```

`antivpn-core` builds and tests fully offline (Maven Central only). The four
platform modules additionally need `repo.papermc.io` (Velocity/Paper/Purpur)
and BungeeCord's Maven coordinates to resolve their `provided`-scope
platform APIs - see [docs/TROUBLESHOOTING.md](docs/TROUBLESHOOTING.md) if
your network blocks those.

## Installing in-game

1. Drop the platform jar(s) you need into `plugins/`:
   - Velocity: `NetworkAntiVPN-Velocity-*.jar`
   - Paper: `NetworkAntiVPN-Paper-*.jar` (or `NetworkAntiVPN-Purpur-*.jar` on Purpur)
   - BungeeCord: `NetworkAntiVPN-BungeeCord-*.jar`
2. Start the server once to generate `config.yml`/`messages.yml` in the
   plugin's data folder.
3. Configure at least one real detection provider (or leave the bundled
   `mock` provider disabled and rely on whitelist/blacklist/country rules
   while you evaluate the system).
4. `/antivpn reload` after editing config, or restart.

## License

MIT - see the license header in [`pom.xml`](pom.xml).
