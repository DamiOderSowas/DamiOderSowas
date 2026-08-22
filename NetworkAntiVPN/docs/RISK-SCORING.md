# Risk Scoring

`scoring.RiskScoringEngine` turns an `AggregatedDetectionResult` plus extra
context into a single 0-100 score.

## Bands

| Score | Level |
|---|---|
| 0-19 | SAFE |
| 20-39 | LOW |
| 40-59 | MEDIUM |
| 60-79 | HIGH |
| 80-100 | CRITICAL |

## Factors and default weights (`risk-scoring.weights` in `config.yml`)

| Factor | Default weight | Triggered by |
|---|---|---|
| `VPN` | 45 | Aggregated `vpn == true` |
| `PROXY` | 40 | Aggregated `proxy == true` |
| `TOR` | 60 | Aggregated `tor == true` |
| `DATACENTER` | 30 | Aggregated `datacenter == true` |
| `HOSTING` | 25 | Aggregated `hosting == true` |
| `RESIDENTIAL_PROXY` | 50 | Aggregated `residentialProxy == true` |
| `IP_REPUTATION` | 20 | Scaled by the average provider-reported `riskScore` (0-100), where providers report one |
| `PROVIDER_AGREEMENT` | 10 | Suspicious result with `providerAgreement >= 0.8` |
| `PROVIDER_DISAGREEMENT` | 5 | Suspicious result with `providerAgreement < 0.5` (uncertainty penalty) |
| `COUNTRY_RISK` | 15 | Resolved country is in `countries.high-risk-countries` |
| `HISTORICAL_DETECTION` | 15 | The player's previous connection scored HIGH or above |

Every applicable factor's weight is summed, then clamped to `[0, 100]`.
Weights are fully configurable; set one to `0` to disable it entirely.

## Why weights, not fixed rules, drive the score

A weight-sum model lets an operator tune sensitivity (e.g. de-emphasize
`HOSTING` for a network with many legitimate home-router-behind-CGNAT
players, or emphasize `TOR`) without a code change or a rebuild.

## Relationship to decisions

The score and its `RiskLevel` are one of several inputs the
[decision engine](DECISIONS.md) can trigger on (`RISK_SAFE` ... `RISK_CRITICAL`
triggers) - see that document for how score and detection flags combine.
