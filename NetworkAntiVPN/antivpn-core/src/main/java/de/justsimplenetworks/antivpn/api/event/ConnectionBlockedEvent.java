package de.justsimplenetworks.antivpn.api.event;

import de.justsimplenetworks.antivpn.api.ConnectionContext;
import de.justsimplenetworks.antivpn.api.DecisionResult;

import java.util.Objects;

/** Fired when the final decision blocks the connection (BLOCK, TEMPORARY_BLOCK or RATE_LIMITED). */
public final class ConnectionBlockedEvent extends AntiVpnEvent {

    private final DecisionResult decisionResult;

    public ConnectionBlockedEvent(ConnectionContext context, DecisionResult decisionResult) {
        super(context);
        this.decisionResult = Objects.requireNonNull(decisionResult, "decisionResult must not be null");
    }

    public DecisionResult decisionResult() {
        return decisionResult;
    }
}
