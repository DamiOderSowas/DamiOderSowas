package de.justsimplenetworks.antivpn.country;

import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class CountryFilterServiceTest {

    @Test
    void euOnlyAllowsEuMember() {
        CountryRule rule = new CountryRule(true, false, false, Set.of(), Set.of(), Set.of(), Set.of(), Set.of());
        CountryFilterService service = new CountryFilterService(rule, Map.of(), Map.of());
        assertTrue(service.evaluate("DE", "EU", null, null).allowed());
    }

    @Test
    void euOnlyBlocksNonEuCountry() {
        CountryRule rule = new CountryRule(true, false, false, Set.of(), Set.of(), Set.of(), Set.of(), Set.of());
        CountryFilterService service = new CountryFilterService(rule, Map.of(), Map.of());
        assertFalse(service.evaluate("US", "NA", null, null).allowed());
    }

    @Test
    void europeOnlyAllowsNonEuEuropeanCountry() {
        CountryRule rule = new CountryRule(false, true, false, Set.of(), Set.of(), Set.of(), Set.of(), Set.of());
        CountryFilterService service = new CountryFilterService(rule, Map.of(), Map.of());
        assertTrue(service.evaluate("GB", "EU", null, null).allowed());
        assertFalse(service.evaluate("US", "NA", null, null).allowed());
    }

    @Test
    void countryAllowlistRestrictsToListedCountries() {
        CountryRule rule = new CountryRule(false, false, false, Set.of("DE", "FR"), Set.of(), Set.of(), Set.of(), Set.of());
        CountryFilterService service = new CountryFilterService(rule, Map.of(), Map.of());
        assertTrue(service.evaluate("DE", "EU", null, null).allowed());
        assertFalse(service.evaluate("IT", "EU", null, null).allowed());
    }

    @Test
    void countryBlocklistBlocksListedCountries() {
        CountryRule rule = new CountryRule(false, false, false, Set.of(), Set.of("CN", "RU"), Set.of(), Set.of(), Set.of());
        CountryFilterService service = new CountryFilterService(rule, Map.of(), Map.of());
        assertFalse(service.evaluate("CN", "AS", null, null).allowed());
        assertTrue(service.evaluate("DE", "EU", null, null).allowed());
    }

    @Test
    void unknownCountryAllowedByDefault() {
        CountryRule rule = CountryRule.disabled();
        CountryFilterService service = new CountryFilterService(rule, Map.of(), Map.of());
        assertTrue(service.evaluate(null, null, null, null).allowed());
    }

    @Test
    void unknownCountryBlockedWhenConfigured() {
        CountryRule rule = new CountryRule(false, false, true, Set.of(), Set.of(), Set.of(), Set.of(), Set.of());
        CountryFilterService service = new CountryFilterService(rule, Map.of(), Map.of());
        assertFalse(service.evaluate(null, null, null, null).allowed());
    }

    @Test
    void perServerRuleOverridesGlobal() {
        CountryRule global = CountryRule.disabled();
        CountryRule serverRule = new CountryRule(true, false, false, Set.of(), Set.of(), Set.of(), Set.of(), Set.of());
        CountryFilterService service = new CountryFilterService(global, Map.of("survival", serverRule), Map.of());
        assertFalse(service.evaluate("US", "NA", "survival", null).allowed());
        assertTrue(service.evaluate("US", "NA", "lobby", null).allowed());
    }

    @Test
    void highRiskCountryFlaggedButNotBlocked() {
        CountryRule rule = new CountryRule(false, false, false, Set.of(), Set.of(), Set.of(), Set.of(), Set.of("BR"));
        CountryFilterService service = new CountryFilterService(rule, Map.of(), Map.of());
        CountryFilterResult result = service.evaluate("BR", "SA", null, null);
        assertTrue(result.allowed());
        assertTrue(result.highRisk());
    }
}
