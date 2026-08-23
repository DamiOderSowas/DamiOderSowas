package de.justsimplenetworks.antivpn.api.event;

import de.justsimplenetworks.antivpn.api.ConnectionContext;
import de.justsimplenetworks.antivpn.api.DecisionResult;

import java.util.Objects;

/** Fired for every connection once the decision engine has produced a final {@link DecisionResult}. */
public final class ConnectionDecisionEvent extends AntiVpnEvent {

    private final DecisionResult decisionResult;

    public ConnectionDecisionEvent(ConnectionContext context, DecisionResult decisionResult) {
        super(context);
        this.decisionResult = Objects.requireNonNull(decisionResult, "decisionResult must not be null");
    }

    public DecisionResult decisionResult() {
        return decisionResult;
    }
}
