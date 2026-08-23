# Security

## SSRF protection (`security.SsrfGuard`)

Every outbound provider HTTP request is validated before it is sent:

- Only `https://` URLs are allowed (rejects `file:`, `gopher:`, plaintext
  `http:`, etc.).
- The `{ip}` placeholder in a provider's `url-template` may never appear in
  the host/authority component - rejected at provider construction time
  (`SsrfGuard.validateTemplate`), so a connecting player's IP can never
  redirect a request to a different host.
- Before every request, the target host is checked: a literal IP host must
  not be private/loopback/link-local/reserved
  (`util.IpAddressUtils.isPrivateOrReserved`); a hostname host is resolved
  and **every** resolved address is checked the same way, to catch
  DNS-rebinding-style redirection toward internal infrastructure.

## No DNS lookups on unvalidated input

`util.IpAddressUtils.isLiteralIpAddress` validates purely with regular
expressions - it never calls `InetAddress.getByName` (which can trigger a
real DNS query for a non-literal string) on attacker-influenced input.
Every other method in that class only accepts input already proven literal.

## Response size limits

`security.ResponseSizeLimiter` caps every provider HTTP response at 256 KB,
aborting the request as soon as the cap is exceeded, instead of buffering
an unbounded amount of provider- or attacker-controlled data in memory.

## Timeouts, retries, circuit breaking

- Per-provider timeout (`detection.per-provider-timeout-seconds`).
- No automatic retries on failure (a failed/timed-out provider is simply
  excluded from that connection's vote - see [DETECTION.md](DETECTION.md));
  this avoids amplifying load on an already-struggling provider.
- Provider health tracking (`providers.ProviderHealthTracker`) marks a
  provider `OFFLINE` after 5 consecutive failures and `DEGRADED` at a 30%
  error rate over its last 20 requests, which operators can observe via
  `/antivpn providers` - a de facto circuit breaker signal for the
  aggregator's rate limiting.

## Concurrency limits

`detection.max-concurrent-provider-requests` bounds a dedicated executor
that all provider I/O runs through, regardless of how many players connect
simultaneously - see [ARCHITECTURE.md](ARCHITECTURE.md).

## Provider rate limits

Enforced per provider before a request is sent (`ratelimit.RateLimiterService`)
- see [RATE-LIMIT.md](RATE-LIMIT.md).

## Bounded caches and queues

`cache.TtlCache` (backing both the detection cache and the provider-result
cache) has a hard `maxEntries`; once full, an existing entry is evicted
before a new one is added - it never grows without bound under a flood of
distinct IPs. The rate limiter's per-IP/per-player counter maps are swept
of idle entries every 5 minutes for the same reason.

## Log injection

`security.LogSanitizer` strips CR/LF/control characters (and truncates to
256 chars) from any value that ultimately originates from a player or a
provider response before it reaches a log line, preventing forged log
entries. `logging.SafeLogger` applies this automatically to every `String`
argument logged through it.

## API key handling

Provider API keys are **never** stored in `config.yml`. `api-key-env`
names an environment variable (e.g. `ANTIVPN_PROVIDER_EXAMPLE_KEY`);
`providers.ProviderConfig.resolveApiKey()` reads it at request time via
`System.getenv`. Keys are never logged (log statements around provider
requests only ever reference the provider *name*, never headers/query
parameters).

## No external requests from unvalidated user input

The only user-influenced value ever included in a provider request is the
connecting player's own already-validated literal IP address, URL-encoded
before insertion into the request. No other player-supplied text (player
name, chat, command arguments beyond an admin-supplied IP for
`/antivpn checkip`) is ever used to build an outbound request.

## Manipulated provider responses

Provider JSON responses are parsed defensively
(`util.JsonPathUtils`): a missing or wrong-typed field is treated as
"unknown" (`null`), never coerced or guessed. Malformed JSON produces a
failed `DetectionResult` (excluded from aggregation), not an exception that
could crash the pipeline.

## Reporting a vulnerability

Please report security issues privately to the JustSimpleNetworks
organization rather than filing a public issue.
