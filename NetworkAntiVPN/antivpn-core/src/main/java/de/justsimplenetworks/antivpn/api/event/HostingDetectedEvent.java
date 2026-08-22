package de.justsimplenetworks.antivpn.api.event;

import de.justsimplenetworks.antivpn.api.AggregatedDetectionResult;
import de.justsimplenetworks.antivpn.api.ConnectionContext;

/** Fired when the aggregated verdict flags the connecting IP as hosting/datacenter space. */
public final class HostingDetectedEvent extends DetectionFlagEvent {
    public HostingDetectedEvent(ConnectionContext context, AggregatedDetectionResult result) {
        super(context, result);
    }
}
