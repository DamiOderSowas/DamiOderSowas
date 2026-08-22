package de.justsimplenetworks.antivpn.country;

import de.justsimplenetworks.antivpn.api.DecisionReason;

import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Evaluates country/continent access rules, including EU-only and
 * Europe-only modes, with support for server- and server-group-specific
 * overrides. Country/continent data always comes from the aggregated
 * detection result (i.e. IP geolocation via the configured providers) -
 * never from reverse DNS, per the specification.
 */
public final class CountryFilterService {

    private final AtomicReference<CountryRule> globalRule;
    private final AtomicReference<Map<String, CountryRule>> serverRules;
    private final AtomicReference<Map<String, CountryRule>> serverGroupRules;

    public CountryFilterService(CountryRule globalRule, Map<String, CountryRule> serverRules,
                                 Map<String, CountryRule> serverGroupRules) {
        this.globalRule = new AtomicReference<>(Objects.requireNonNull(globalRule));
        this.serverRules = new AtomicReference<>(Map.copyOf(serverRules));
        this.serverGroupRules = new AtomicReference<>(Map.copyOf(serverGroupRules));
    }

    public void reload(CountryRule globalRule, Map<String, CountryRule> serverRules,
                        Map<String, CountryRule> serverGroupRules) {
        this.globalRule.set(globalRule);
        this.serverRules.set(Map.copyOf(serverRules));
        this.serverGroupRules.set(Map.copyOf(serverGroupRules));
    }

    public CountryFilterResult evaluate(String country, String continent, String serverName, String serverGroup) {
        CountryRule rule = resolveRule(serverName, serverGroup);

        String countryCode = country == null ? null : country.toUpperCase(Locale.ROOT);
        String continentCode = continent == null ? null : continent.toUpperCase(Locale.ROOT);

        if (countryCode == null) {
            if (rule.blockUnknownCountry()) {
                return CountryFilterResult.block(DecisionReason.COUNTRY_BLOCKED);
            }
            return CountryFilterResult.allow(false);
        }

        if (rule.euOnly() && !EuCountries.isEuMember(countryCode)) {
            return CountryFilterResult.block(DecisionReason.COUNTRY_NOT_EU);
        }
        if (!rule.euOnly() && rule.europeOnly() && !EuCountries.isEuropean(countryCode)) {
            return CountryFilterResult.block(DecisionReason.COUNTRY_NOT_EUROPE);
        }
        if (!rule.allowedCountries().isEmpty() && !rule.allowedCountries().contains(countryCode)) {
            return CountryFilterResult.block(DecisionReason.COUNTRY_BLOCKED);
        }
        if (rule.blockedCountries().contains(countryCode)) {
            return CountryFilterResult.block(DecisionReason.COUNTRY_BLOCKED);
        }
        if (!rule.allowedContinents().isEmpty()
                && (continentCode == null || !rule.allowedContinents().contains(continentCode))) {
            return CountryFilterResult.block(DecisionReason.CONTINENT_BLOCKED);
        }
        if (continentCode != null && rule.blockedContinents().contains(continentCode)) {
            return CountryFilterResult.block(DecisionReason.CONTINENT_BLOCKED);
        }

        return CountryFilterResult.allow(rule.highRiskCountries().contains(countryCode));
    }

    private CountryRule resolveRule(String serverName, String serverGroup) {
        if (serverName != null) {
            CountryRule perServer = serverRules.get().get(serverName);
            if (perServer != null) {
                return perServer;
            }
        }
        if (serverGroup != null) {
            CountryRule perGroup = serverGroupRules.get().get(serverGroup);
            if (perGroup != null) {
                return perGroup;
            }
        }
        return globalRule.get();
    }
}
