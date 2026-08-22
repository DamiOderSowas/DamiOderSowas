package de.justsimplenetworks.antivpn.country;

import java.util.Set;

/**
 * ISO 3166-1 alpha-2 country codes used by EU-only / Europe-only filtering.
 * <p>
 * {@link #EU_MEMBER_STATES} is the 27 member states of the European Union as
 * of 2026 (post-Brexit membership). {@link #EUROPEAN_COUNTRIES} is the
 * broader geographic continent of Europe used by {@code europe-only} mode -
 * it is a superset of {@link #EU_MEMBER_STATES} and additionally includes
 * non-EU European states (United Kingdom, Switzerland, Norway, Iceland,
 * Liechtenstein, Balkan states, Ukraine, and Europe's microstates). Russia
 * and Turkey are intentionally excluded as they are only partially in
 * Europe and are conventionally treated as their own case; add them to a
 * custom {@code country.allowlist} in {@code config.yml} if your network
 * wants to include them.
 */
public final class EuCountries {

    private EuCountries() {
    }

    public static final Set<String> EU_MEMBER_STATES = Set.of(
            "AT", "BE", "BG", "HR", "CY", "CZ", "DK", "EE", "FI", "FR",
            "DE", "GR", "HU", "IE", "IT", "LV", "LT", "LU", "MT", "NL",
            "PL", "PT", "RO", "SK", "SI", "ES", "SE"
    );

    public static final Set<String> EUROPEAN_COUNTRIES = Set.of(
            "AT", "BE", "BG", "HR", "CY", "CZ", "DK", "EE", "FI", "FR",
            "DE", "GR", "HU", "IE", "IT", "LV", "LT", "LU", "MT", "NL",
            "PL", "PT", "RO", "SK", "SI", "ES", "SE",
            "GB", "CH", "NO", "IS", "LI", "AD", "MC", "SM", "VA",
            "AL", "BA", "ME", "MK", "RS", "XK", "MD", "UA", "BY"
    );

    public static boolean isEuMember(String countryCode) {
        return countryCode != null && EU_MEMBER_STATES.contains(countryCode.toUpperCase(java.util.Locale.ROOT));
    }

    public static boolean isEuropean(String countryCode) {
        return countryCode != null && EUROPEAN_COUNTRIES.contains(countryCode.toUpperCase(java.util.Locale.ROOT));
    }
}
