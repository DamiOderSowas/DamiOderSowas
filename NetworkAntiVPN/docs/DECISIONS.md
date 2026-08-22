# Decisions

`decision.DecisionEngine` evaluates a configurable, ordered rule table
(`decisions.rules` in `config.yml`) against the aggregated detection result
and risk level. **No rule is hard-coded in Java.** Rules are evaluated top
to bottom; the first match wins.

## Possible decisions (`api.Decision`)

| Decision | Meaning |
|---|---|
| `ALLOW` | Allowed, no extra logging. |
| `ALLOW_WITH_LOG` | Allowed, logged at an elevated level for staff review. |
| `ALLOW_WITH_WARNING` | Allowed, player receives a warning message. |
| `REQUIRE_VERIFICATION` | Additional verification required (kicked with a "contact staff" message today; a real verification flow is a future extension point). |
| `TEMPORARY_BLOCK` | Blocked for a limited, auto-expiring duration (used for temporary blacklist entries). |
| `BLOCK` | Blocked outright. |
| `BYPASS` | Bypasses all further checks (whitelist/bypass short-circuit). |
| `RATE_LIMITED` | Rejected because a rate limit was exceeded. |

## Valid triggers (`decision.DecisionTrigger`)

`VPN`, `PROXY`, `HOSTING`, `DATACENTER`, `TOR`, `RESIDENTIAL_PROXY`,
`RESIDENTIAL`, `RISK_SAFE`, `RISK_LOW`, `RISK_MEDIUM`, `RISK_HIGH`,
`RISK_CRITICAL`, `UNKNOWN` (matches when detection ran successfully and
nothing else matched - i.e. a clean connection).

## Shipped default rule table

```yaml
decisions:
  rules:
    - trigger: TOR
      decision: BLOCK
    - trigger: VPN
      decision: BLOCK
    - trigger: PROXY
      decision: BLOCK
    - trigger: RESIDENTIAL_PROXY
      decision: REQUIRE_VERIFICATION
    - trigger: DATACENTER
      decision: REQUIRE_VERIFICATION
    - trigger: HOSTING
      decision: REQUIRE_VERIFICATION
    - trigger: RISK_CRITICAL
      decision: BLOCK
    - trigger: RISK_HIGH
      decision: REQUIRE_VERIFICATION
    - trigger: RISK_MEDIUM
      decision: ALLOW_WITH_WARNING
  default-decision: ALLOW_WITH_LOG
```

## What happens before the rule table even runs

Whitelist, bypass, and blacklist are resolved **before** detection even
starts (see [ARCHITECTURE.md](ARCHITECTURE.md)) and short-circuit straight
to `BYPASS`/`BLOCK`/`TEMPORARY_BLOCK` - they never reach the rule table.
Country-filter blocks and rate limiting also short-circuit before/around the
rule table. The rule table only decides what happens for a connection that
survived all of those and was actually analyzed.

If detection could not run at all (every provider failed), the rule table
is skipped entirely in favour of `fallback.policy` - see
[PROVIDERS.md#fallback](PROVIDERS.md#fallback).

## `DecisionResult`

Every decision carries: the `Decision`, a `DecisionReason` (e.g.
`VPN_DETECTED`, `WHITELIST_IP`, `RATE_LIMITED`, `COUNTRY_NOT_EU`, ...), the
risk score/level, the underlying `AggregatedDetectionResult` (or `null` for
a short-circuited decision), a `messages.yml` key
(`decision.DecisionMessageKeys`), and - for temporary blocks - an
expiry timestamp.
