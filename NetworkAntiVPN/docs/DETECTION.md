# Detection

## What is detected

Per provider, per IP (`api.DetectionResult`): VPN, proxy, hosting,
datacenter, Tor, residential proxy, residential, country, continent, ASN,
ISP, organization, reverse-DNS hostname (informational only), a 0.0-1.0
confidence, an optional provider-reported 0-100 risk indicator, evidence
strings, latency, whether it was cache-served, and an error if the request
failed.

Every field except `ip`/`timestamp`/`provider` is nullable-by-convention:
a provider that does not know something must leave it `null`, never guess.

## Aggregation (`detection.DetectionAggregator`)

1. Every enabled provider is queried **in parallel**, each on its own timeout
   (`detection.per-provider-timeout-seconds`).
2. A provider's individual result is cached (`cache.provider-cache-ttl-seconds`)
   so a re-aggregation (e.g. after `/antivpn reload`) doesn't necessarily
   re-query every provider.
3. A provider's own rate limit (`rate-limit-per-minute`) is enforced before
   the request is even sent; exceeding it skips that provider for this
   check (recorded as `RATE_LIMITED` health, not a hard failure).
4. For each boolean flag (vpn/proxy/hosting/datacenter/tor/residentialProxy/
   residential), a weighted vote is taken: `weight(provider) * confidence(result)`
   for `true` vs. total weighted-known votes. The flag is `true` if that
   ratio meets `detection.provider-agreement-threshold` (default `0.5`).
5. For country/continent/ASN/ISP/organization, the highest-weighted
   non-null value wins (ties broken by provider order).
6. `providerAgreement` = fraction of successful providers whose overall
   "is this suspicious" verdict matches the majority - shown in
   `/antivpn debug`.
7. If **no** provider succeeded, the aggregator returns
   `AggregatedDetectionResult.unknown(...)`, which routes to
   `fallback.policy` in the decision engine instead of the normal rules.

## Why detection never blocks the join

- Provider I/O runs on a bounded, dedicated executor
  (`detection.max-concurrent-provider-requests`), never on the Minecraft
  main thread or the proxy's Netty event loop.
- Every provider call has a hard timeout; a slow/offline provider is simply
  excluded from that connection's vote.
- The whole aggregation additionally has an outer safety timeout
  (`timeouts.overall-analysis-seconds`).
- The cache means a repeat connection from the same IP within the TTL never
  triggers a new provider request at all.

See [PERFORMANCE notes in ARCHITECTURE.md](ARCHITECTURE.md) and
[docs/RATE-LIMIT.md](RATE-LIMIT.md) for the concurrency/rate-limit controls
that keep this safe under connection spam.

## Bedrock vs. Java platform

`api.Platform` (`JAVA`/`BEDROCK`/`UNKNOWN`) is set by the platform
integration layer. Velocity has no native Bedrock concept; the bundled
adapter uses a best-effort Floodgate-style username-prefix heuristic - see
`integration.velocity.VelocityConnectionListener#detectPlatform` and adjust
it to your Geyser/Floodgate configuration.
