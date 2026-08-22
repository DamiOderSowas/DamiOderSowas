package de.justsimplenetworks.antivpn.api.event;

import de.justsimplenetworks.antivpn.api.AggregatedDetectionResult;
import de.justsimplenetworks.antivpn.api.ConnectionContext;

/** Fired when the aggregated verdict flags the connecting IP as a VPN. */
public final class VPNDetectedEvent extends DetectionFlagEvent {
    public VPNDetectedEvent(ConnectionContext context, AggregatedDetectionResult result) {
        super(context, result);
    }
}
