# Privacy

## What is stored, and where

| Data | Location | Retention |
|---|---|---|
| Aggregated detection verdict (country, ASN, ISP, flags) | In-memory `DetectionCache`, keyed by IP | `cache.ttl-seconds` (default 1h), evicted on expiry or restart |
| Per-provider raw result | In-memory `ProviderResultCache`, keyed by provider+IP | `cache.provider-cache-ttl-seconds` (default 1h) |
| Session decision (`DecisionResult`) | In-memory, keyed by player UUID | Cleared on disconnect; swept after 2h regardless |
| Player risk profile (`api.PlayerRiskProfile`) | In-memory, keyed by player UUID | Overwritten on each connection; not persisted to disk |
| Whitelist/blacklist/bypass entries | `<data>/whitelist.json`, `blacklist.json`, `bypass.json` | Permanent entries: until removed. Temporary entries: until expiry (auto-pruned). |

Nothing above is written to a database; there is none in this standalone
deployment (see [INTEGRATION.md](INTEGRATION.md) for the planned
NetworkDatabase/NetworkRedis future).

## Raw IP vs. hashed IP

By default (`privacy.store-raw-ip: false`), `PlayerRiskProfile.ip()` is
`null` - only `PlayerRiskProfile.ipHash()`, a salted SHA-256 hash
(`security.IpHasher`), is populated. This lets other systems (e.g. a future
NetworkAntiMultiAccount) correlate repeat connections from the same address
without this plugin retaining the address itself.

Set `privacy.store-raw-ip: true` only if you have a specific, documented
need (e.g. a legal/abuse-response requirement) for the raw address to be
available via `AntiVpnService.getRiskProfile(...)`.

## Hash salt

`privacy.hash-salt` **must** be changed from the shipped placeholder to a
real random value per installation. It makes IP hashes non-comparable
across independently configured networks and non-reversible via a plain
IPv4-space rainbow table. `security.IpHasher` refuses to start with an
empty salt.

## Retention

`privacy.retention-days` documents the intended retention window for
whatever a future persistent store (NetworkDatabase/NetworkRedis) will
enforce; the current in-memory-only implementation already retains
everything for far less than typical `retention-days` values (caches
measured in hours, session data cleared on disconnect).

## What NetworkAntiVPN never does

- No reverse DNS is used for country/geolocation decisions (see
  [COUNTRY-FILTER.md](COUNTRY-FILTER.md)) - only IP-intelligence provider data.
- No chat content, inventory, or gameplay data is inspected or stored.
- No data is sent anywhere except: (a) the connecting player's own IP, to
  the detection providers you configure, and (b) the computed decision, to
  the backend server behind your own proxy (see [VELOCITY.md](VELOCITY.md)).
- Provider API keys and response bodies are never logged in full (see
  [SECURITY.md](SECURITY.md)).

## GDPR-relevant notes for operators

If your network serves EU users, treat the IP address (and its hash) as
personal data under GDPR. This document describes the technical retention
behaviour; consult your own legal counsel for your network's privacy
policy and lawful basis for processing.
