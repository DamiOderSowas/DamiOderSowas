# Providers

A `providers.DetectionProvider` is a single, swappable IP-intelligence
source. NetworkAntiVPN ships two implementations and lets you configure any
number of instances of the HTTP one:

## `MockDetectionProvider`

Performs **no** network I/O. By default it returns "no verdict" (every
boolean flag `null`, confidence `0.0`, an evidence note explaining why) for
every IP - it is deliberately honest about having no real intelligence
rather than fabricating a detection. It ships **disabled** in `config.yml`.

Use it to:
- exercise the whole pipeline (cache, whitelist/blacklist, decisions,
  commands, events) without any external dependency;
- as the target of unit tests, where a custom resolver function can inject
  any specific verdict, a timeout, or an error (see
  `antivpn-core/src/test/java/.../detection/DetectionAggregatorTest.java`).

## `ConfigurableHttpDetectionProvider`

A generic JSON-over-HTTPS provider. **No vendor endpoint or response schema
is hard-coded anywhere in this project** - you configure:

```yaml
providers:
  example:
    enabled: true
    priority: 10
    weight: 1.5
    timeout-seconds: 3
    rate-limit-per-minute: 60
    capabilities: [VPN, PROXY, HOSTING, DATACENTER, TOR, RESIDENTIAL_PROXY, COUNTRY, ASN, ISP, ORGANIZATION, REPUTATION]
    url-template: "https://api.example.com/v1/check/{ip}"   # must contain {ip}
    api-key-env: "ANTIVPN_PROVIDER_EXAMPLE_KEY"               # env var name, never a literal key
    api-key-header: "Authorization"                            # or use api-key-query-param instead
    api-key-query-param: ""
    field-mapping:
      "security.vpn": vpn
      "security.proxy": proxy
      "security.tor": tor
      "network.hosting": hosting
      "network.datacenter": datacenter
      "location.country": country
      "location.continent": continent
      "network.asn": asn
      "network.isp": isp
      "network.organization": organization
      "risk.score": riskScore
```

`field-mapping` keys are dot-notation JSON paths into the provider's
response body; values must be one of: `vpn`, `proxy`, `hosting`,
`datacenter`, `tor`, `residentialProxy`, `residential`, `country`,
`continent`, `isp`, `organization`, `hostname`, `asn`, `riskScore`.

### Secrets

`api-key-env` names an **environment variable** holding the key - never put
a literal key in `config.yml`. See [SECURITY.md](SECURITY.md).

### Hardening applied to every request

- HTTPS only (rejected at startup/request time otherwise).
- SSRF guard: the resolved host (and the `{ip}` placeholder position) is
  checked against private/loopback/link-local/reserved ranges before every
  request; the `{ip}` placeholder is rejected if it appears in the host part
  of `url-template`.
- No redirects followed.
- Hard response size cap (256 KB) - `security.ResponseSizeLimiter`.
- Per-request timeout (`timeout-seconds`).

## Writing your own provider

Implement `providers.DetectionProvider` (four methods: `name()`, `config()`,
`health()`, `detect(String ip)`), never block the calling thread, never
throw (report failures via `DetectionResult.error(...)`), and never claim a
flag you don't have evidence for (leave it `null`). Register it in
whatever wires up `core.AntiVpnBootstrap` for your deployment, or send a PR
adding a config-driven build path similar to `ConfigurableHttpDetectionProvider`.

## Multi-provider aggregation

All enabled providers are queried in parallel; each provider's answer is
weighted by its `weight * confidence` when the aggregator votes on each
boolean flag (threshold: `detection.provider-agreement-threshold`). See
[DETECTION.md](DETECTION.md).

## Provider health

Tracked per provider (`providers.ProviderHealthTracker`): `ONLINE`,
`DEGRADED` (elevated error rate in the last 20 requests), `RATE_LIMITED`
(after an HTTP 429), or `OFFLINE` (5 consecutive failures). View with
`/antivpn providers` / `/antivpn status`.

## Fallback

When **every** provider fails/times out/is disabled, `fallback.policy`
decides the outcome instead of the normal decision rules:

| Policy | Behaviour | When to use |
|---|---|---|
| `FAIL_OPEN` (default) | Allow, logged (`ALLOW_WITH_LOG`, reason `PROVIDER_FALLBACK`). | Default recommendation: a provider outage should never lock an entire live network out. |
| `FAIL_CLOSED` | Block until a provider is reachable again. | Networks that must never accept an unverified connection. |
| `REQUIRE_VERIFICATION` | Middle ground. | Balances availability and security. |

This is logged and counted in metrics regardless of policy, so staff notice
an outage even under `FAIL_OPEN`.
