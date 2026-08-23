package de.justsimplenetworks.antivpn.api.event;

import de.justsimplenetworks.antivpn.api.AggregatedDetectionResult;
import de.justsimplenetworks.antivpn.api.ConnectionContext;

import java.util.Objects;

/** Fired once detection + aggregation has produced a verdict, before the decision engine runs. */
public final class ConnectionAnalysisCompletedEvent extends AntiVpnEvent {

    private final AggregatedDetectionResult result;

    public ConnectionAnalysisCompletedEvent(ConnectionContext context, AggregatedDetectionResult result) {
        super(context);
        this.result = Objects.requireNonNull(result, "result must not be null");
    }

    public AggregatedDetectionResult result() {
        return result;
    }
}
