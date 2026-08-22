package de.justsimplenetworks.antivpn.country;

import java.util.Set;

/**
 * One country-filtering policy. The global policy comes from
 * {@code country.*} in {@code config.yml}; per-server or per-server-group
 * overrides completely replace the global policy for that server/group.
 *
 * @param euOnly              only allow EU member states ({@link EuCountries#EU_MEMBER_STATES})
 * @param europeOnly          only allow the wider Europe continent ({@link EuCountries#EUROPEAN_COUNTRIES});
 *                             ignored if {@link #euOnly()} is set
 * @param blockUnknownCountry block a connection if the country could not be resolved at all
 * @param allowedCountries    if non-empty, only these ISO country codes are allowed (allowlist wins over blocklist)
 * @param blockedCountries    these ISO country codes are always blocked
 * @param allowedContinents   if non-empty, only these continent codes are allowed
 * @param blockedContinents   these continent codes are always blocked
 * @param highRiskCountries   countries that don't block the connection but are flagged as an elevated risk factor
 */
public record CountryRule(
        boolean euOnly,
        boolean europeOnly,
        boolean blockUnknownCountry,
        Set<String> allowedCountries,
        Set<String> blockedCountries,
        Set<String> allowedContinents,
        Set<String> blockedContinents,
        Set<String> highRiskCountries
) {
    public CountryRule {
        allowedCountries = allowedCountries == null ? Set.of() : Set.copyOf(allowedCountries);
        blockedCountries = blockedCountries == null ? Set.of() : Set.copyOf(blockedCountries);
        allowedContinents = allowedContinents == null ? Set.of() : Set.copyOf(allowedContinents);
        blockedContinents = blockedContinents == null ? Set.of() : Set.copyOf(blockedContinents);
        highRiskCountries = highRiskCountries == null ? Set.of() : Set.copyOf(highRiskCountries);
    }

    public static CountryRule disabled() {
        return new CountryRule(false, false, false, Set.of(), Set.of(), Set.of(), Set.of(), Set.of());
    }
}
