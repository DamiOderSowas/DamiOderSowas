package de.justsimplenetworks.antivpn.api.event;

import de.justsimplenetworks.antivpn.api.AggregatedDetectionResult;
import de.justsimplenetworks.antivpn.api.ConnectionContext;

import java.util.Objects;

/** Base type for the "a specific detection flag fired" family of events. */
public abstract class DetectionFlagEvent extends AntiVpnEvent {

    private final AggregatedDetectionResult result;

    protected DetectionFlagEvent(ConnectionContext context, AggregatedDetectionResult result) {
        super(context);
        this.result = Objects.requireNonNull(result, "result must not be null");
    }

    public AggregatedDetectionResult result() {
        return result;
    }
}
