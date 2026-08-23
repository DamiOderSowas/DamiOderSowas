package de.justsimplenetworks.antivpn.decision;

import de.justsimplenetworks.antivpn.api.Decision;
import de.justsimplenetworks.antivpn.api.DecisionReason;

import java.util.Objects;

/** One configurable "if trigger, then decision" rule; see {@code decisions} in {@code config.yml}. */
public record DecisionRule(DecisionTrigger trigger, Decision decision, DecisionReason reason) {

    public DecisionRule {
        Objects.requireNonNull(trigger, "trigger must not be null");
        Objects.requireNonNull(decision, "decision must not be null");
        Objects.requireNonNull(reason, "reason must not be null");
    }
}
