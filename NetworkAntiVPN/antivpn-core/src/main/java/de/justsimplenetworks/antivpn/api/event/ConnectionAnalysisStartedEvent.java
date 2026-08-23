package de.justsimplenetworks.antivpn.api.event;

import de.justsimplenetworks.antivpn.api.ConnectionContext;

/** Fired the moment a connection enters the analysis pipeline (before cache/provider lookup). */
public final class ConnectionAnalysisStartedEvent extends AntiVpnEvent {
    public ConnectionAnalysisStartedEvent(ConnectionContext context) {
        super(context);
    }
}
