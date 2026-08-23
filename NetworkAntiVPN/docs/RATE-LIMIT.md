# Rate Limiting

`ratelimit.RateLimiterService` protects the proxy/backend against
connection spam using sliding-window counters (`ratelimit.SlidingWindowCounter`) -
an exact count of events within a trailing time window, not a fixed-bucket
approximation.

## Configuration

```yaml
rate-limit:
  enabled: true
  max-connections-per-ip: 5
  window-seconds: 10
  max-connections-per-player: 10
  player-window-seconds: 10
```

Exceeding either limit produces `Decision.RATE_LIMITED` (checked first in
the pipeline, before bypass/whitelist/blacklist/detection).

## Provider rate limiting

Independently, each provider's own `rate-limit-per-minute` (see
[PROVIDERS.md](PROVIDERS.md)) is enforced by the same sliding-window
mechanism before a request is sent to that provider - exceeding it skips
just that provider for the current check (recorded as a `RATE_LIMITED`
health status), not the whole connection.

## IPv4 vs. IPv6

Both are rate-limited as literal address strings (via
`util.IpAddressUtils.normalize`) - an IPv6 `/64` a residential ISP hands out
per customer is **not** aggregated into one bucket by default. If your
network sees IPv6 abuse via address rotation within a single `/64`, you can
add subnet-based limiting on top of `RateLimiterService.tryAcquireForIp` by
normalizing to a CIDR prefix before calling it - this is a deliberate,
documented tradeoff rather than an oversight, since aggressively bucketing
by `/64` would also rate-limit unrelated players sharing the same ISP
prefix.

## Memory bounds

Idle counters (no recent activity) are swept every 5 minutes so the
tracked-IP map cannot grow unbounded under a distributed connection-spam
attack from many different addresses.
