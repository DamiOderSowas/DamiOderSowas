package de.justsimplenetworks.antivpn.api.event;

import de.justsimplenetworks.antivpn.api.AggregatedDetectionResult;
import de.justsimplenetworks.antivpn.api.ConnectionContext;

/** Fired when the aggregated verdict flags the connecting IP as a generic proxy. */
public final class ProxyDetectedEvent extends DetectionFlagEvent {
    public ProxyDetectedEvent(ConnectionContext context, AggregatedDetectionResult result) {
        super(context, result);
    }
}
