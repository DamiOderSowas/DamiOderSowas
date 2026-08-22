# Configuration Reference

`config.yml` and `messages.yml` are generated in the plugin's data folder on
first start (copied from the bundled defaults). Everything except the
provider list and thread-pool sizing can be changed and applied live with
`/antivpn reload` (see [COMMANDS.md](COMMANDS.md)).

All keys below are read by `config.AntiVpnConfig`; that class is the single
source of truth for defaults if this document and the code ever disagree.

## `general`

| Key | Type | Default | Meaning |
|---|---|---|---|
| `general.enabled` | bool | `true` | Master switch. `false` allows every connection and runs no detection. |
| `general.debug` | bool | `false` | Verbose internal logging (never prints API keys - see [SECURITY.md](SECURITY.md)). |

## `detection`

| Key | Default | Meaning |
|---|---|---|
| `detection.provider-agreement-threshold` | `0.5` | Weighted agreement fraction required for an aggregated flag to be `true`. |
| `detection.per-provider-timeout-seconds` | `3` | Hard timeout per provider request. |
| `detection.max-concurrent-provider-requests` | `16` | Global cap on in-flight provider HTTP requests. |

`timeouts.overall-analysis-seconds` (default `5`) bounds the whole
detection+aggregation step as a safety net.

## `providers`

A map keyed by provider name; see [PROVIDERS.md](PROVIDERS.md) for the full
field reference (`enabled`, `priority`, `weight`, `timeout-seconds`,
`rate-limit-per-minute`, `capabilities`, `url-template`, `api-key-env`,
`api-key-header`, `api-key-query-param`, `field-mapping`).

## `cache`

| Key | Default | Meaning |
|---|---|---|
| `cache.enabled` | `true` | |
| `cache.ttl-seconds` | `3600` | TTL for an aggregated verdict. |
| `cache.max-entries` | `10000` | Bounded size (oldest entry evicted when full). |
| `cache.cleanup-interval-seconds` | `60` | How often expired entries are swept. |
| `cache.provider-cache-ttl-seconds` | `3600` | TTL for a single provider's cached answer. |
| `cache.provider-cache-max-entries` | `20000` | |

## `risk-scoring`

`risk-scoring.weights` maps a `scoring.RiskFactor` name to its point
contribution (0-100 scale target). See [RISK-SCORING.md](RISK-SCORING.md)
for the full factor list and defaults.

## `decisions`

`decisions.rules` is an ordered list of `{trigger, decision[, reason]}`
objects, evaluated top to bottom - first match wins.
`decisions.default-decision` applies when nothing matched. See
[DECISIONS.md](DECISIONS.md) for every valid `trigger`/`decision` value.

## `countries`

| Key | Default | Meaning |
|---|---|---|
| `countries.eu-only` | `false` | Only EU member states. |
| `countries.europe-only` | `false` | Only the wider Europe continent (ignored if `eu-only` is set). |
| `countries.block-unknown-country` | `false` | Block if no country could be resolved. |
| `countries.allowed-countries` / `blocked-countries` | `[]` | ISO 3166-1 alpha-2 codes. |
| `countries.allowed-continents` / `blocked-continents` | `[]` | Continent codes. |
| `countries.high-risk-countries` | `[]` | Feeds the `COUNTRY_RISK` scoring factor; does not block by itself. |
| `countries.servers.<name>.*` | | Per-server override (same fields), completely replaces the global policy for that server. |
| `countries.server-groups.<name>.*` | | Per-server-group override, used if no per-server override matches. |

See [COUNTRY-FILTER.md](COUNTRY-FILTER.md) for the exact EU/Europe lists.

## `whitelist` / `blacklist`

Static, `config.yml`-sourced entries (`ips`, `cidrs`, `players`, `uuids`,
and for blacklist also `asns`). Entries added at runtime via
`/antivpn whitelist|blacklist add` live in `whitelist.json`/`blacklist.json`
in the data folder and are untouched by editing `config.yml`. See
[WHITELIST.md](WHITELIST.md) / [BLACKLIST.md](BLACKLIST.md).

## `temporary-bypass`

`temporary-bypass.enabled` (default `true`) gates the `/antivpn bypass`
command family. See [BYPASS.md](BYPASS.md).

## `rate-limit`

| Key | Default |
|---|---|
| `rate-limit.enabled` | `true` |
| `rate-limit.max-connections-per-ip` | `5` |
| `rate-limit.window-seconds` | `10` |
| `rate-limit.max-connections-per-player` | `10` |
| `rate-limit.player-window-seconds` | `10` |

See [RATE-LIMIT.md](RATE-LIMIT.md).

## `fallback`

`fallback.policy`: `FAIL_OPEN` (default), `FAIL_CLOSED`, or
`REQUIRE_VERIFICATION`. See [PROVIDERS.md#fallback](PROVIDERS.md#fallback).

## `metrics`

`metrics.enabled` (default `true`).

## `privacy`

| Key | Default | Meaning |
|---|---|---|
| `privacy.store-raw-ip` | `false` | If `false`, only a salted hash of the IP is retained in risk profiles. |
| `privacy.hash-salt` | *(placeholder - change it!)* | Salts the IP hash; set a real random value per installation. |
| `privacy.retention-days` | `7` | Documented retention target - see [PRIVACY.md](PRIVACY.md). |

## `velocity` / `paper` / `purpur` / `bungeecord`

| Key | Default | Meaning |
|---|---|---|
| `velocity.early-check` | `true` | Check at login, before backend routing. |
| `velocity.forward-result-to-backend` | `true` | Forward the computed result over a plugin messaging channel. |
| `paper.reuse-proxy-result` | `true` | Reuse a forwarded result instead of re-checking, when available. |
| `bungeecord.early-check` | `true` | Same as `velocity.early-check`, for the BungeeCord module. |

## Reload semantics

`/antivpn reload` re-reads `config.yml`/`messages.yml` and re-applies:
whitelist/blacklist static entries, country rules, risk weights, decision
rules, and messages. It does **not** reconstruct providers, the provider
thread pool, or cache sizing - those require a restart. See
[TROUBLESHOOTING.md](TROUBLESHOOTING.md).
