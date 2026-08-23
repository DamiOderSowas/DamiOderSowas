package de.justsimplenetworks.antivpn.api.event;

import de.justsimplenetworks.antivpn.api.ConnectionContext;
import de.justsimplenetworks.antivpn.api.DecisionResult;

import java.util.Objects;

/** Fired when the final decision allows the connection (ALLOW, ALLOW_WITH_LOG, ALLOW_WITH_WARNING or BYPASS). */
public final class ConnectionAllowedEvent extends AntiVpnEvent {

    private final DecisionResult decisionResult;

    public ConnectionAllowedEvent(ConnectionContext context, DecisionResult decisionResult) {
        super(context);
        this.decisionResult = Objects.requireNonNull(decisionResult, "decisionResult must not be null");
    }

    public DecisionResult decisionResult() {
        return decisionResult;
    }
}
