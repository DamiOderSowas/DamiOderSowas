# Country Filter

`country.CountryFilterService` evaluates EU-only/Europe-only mode and
allow-/blocklists using the country/continent resolved by detection
providers - **never** reverse DNS.

## EU member states (`country.EuCountries.EU_MEMBER_STATES`, 27 countries)

Austria (AT), Belgium (BE), Bulgaria (BG), Croatia (HR), Cyprus (CY),
Czechia (CZ), Denmark (DK), Estonia (EE), Finland (FI), France (FR),
Germany (DE), Greece (GR), Hungary (HU), Ireland (IE), Italy (IT),
Latvia (LV), Lithuania (LT), Luxembourg (LU), Malta (MT), Netherlands (NL),
Poland (PL), Portugal (PT), Romania (RO), Slovakia (SK), Slovenia (SI),
Spain (ES), Sweden (SE).

## Europe (`country.EuCountries.EUROPEAN_COUNTRIES`)

All EU member states above, plus: United Kingdom (GB), Switzerland (CH),
Norway (NO), Iceland (IS), Liechtenstein (LI), Andorra (AD), Monaco (MC),
San Marino (SM), Vatican City (VA), Albania (AL), Bosnia and Herzegovina
(BA), Montenegro (ME), North Macedonia (MK), Serbia (RS), Kosovo (XK),
Moldova (MD), Ukraine (UA), Belarus (BY).

Russia and Turkey are **intentionally excluded** from both lists (they
straddle Europe/Asia and are conventionally handled as a separate case) -
add them to `countries.allowed-countries` yourself if your network wants to
include them.

## Modes

```yaml
countries:
  eu-only: true       # only EU_MEMBER_STATES allowed
  europe-only: false  # ignored when eu-only is true; otherwise widens to EUROPEAN_COUNTRIES
```

Example: with `eu-only: true`, Germany/France/Austria are allowed; USA,
Canada, and China are blocked (reason `COUNTRY_NOT_EU`). With
`europe-only: true` (and `eu-only: false`), the United Kingdom is also
allowed (reason for a block becomes `COUNTRY_NOT_EUROPE`).

## Allow-/blocklists

`countries.allowed-countries`/`blocked-countries` and
`allowed-continents`/`blocked-continents` apply **in addition** to (after)
EU-only/Europe-only. An allowlist, if non-empty, is exclusive - only listed
countries/continents pass.

## High-risk countries

`countries.high-risk-countries` does not block by itself; it feeds the
`COUNTRY_RISK` [risk-scoring](RISK-SCORING.md) factor.

## Unknown country

`countries.block-unknown-country` (default `false`) decides whether a
connection whose country could not be resolved at all is blocked or
allowed.

## Per-server / per-server-group rules

```yaml
countries:
  servers:
    survival:
      eu-only: true
  server-groups:
    minigames:
      europe-only: true
```

A per-server rule, if present for the connecting server, completely
replaces the global policy; otherwise a per-server-group rule is used if
present; otherwise the global policy applies.
