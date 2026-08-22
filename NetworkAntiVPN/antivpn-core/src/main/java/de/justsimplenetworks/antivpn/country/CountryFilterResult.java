package de.justsimplenetworks.antivpn.country;

import de.justsimplenetworks.antivpn.api.DecisionReason;

/**
 * The outcome of {@link CountryFilterService#evaluate}.
 *
 * @param allowed    whether the country/continent is permitted
 * @param reason     why it was blocked, {@code null} if {@link #allowed()}
 * @param highRisk   whether the resolved country is on the high-risk list (does not by itself block)
 */
public record CountryFilterResult(boolean allowed, DecisionReason reason, boolean highRisk) {

    public static CountryFilterResult allow(boolean highRisk) {
        return new CountryFilterResult(true, null, highRisk);
    }

    public static CountryFilterResult block(DecisionReason reason) {
        return new CountryFilterResult(false, reason, false);
    }
}
