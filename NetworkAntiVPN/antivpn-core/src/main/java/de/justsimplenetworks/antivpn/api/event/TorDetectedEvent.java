package de.justsimplenetworks.antivpn.api.event;

import de.justsimplenetworks.antivpn.api.AggregatedDetectionResult;
import de.justsimplenetworks.antivpn.api.ConnectionContext;

/** Fired when the aggregated verdict flags the connecting IP as a Tor exit node. */
public final class TorDetectedEvent extends DetectionFlagEvent {
    public TorDetectedEvent(ConnectionContext context, AggregatedDetectionResult result) {
        super(context, result);
    }
}
