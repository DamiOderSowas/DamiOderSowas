package de.justsimplenetworks.antivpn.api.event;

import de.justsimplenetworks.antivpn.api.AggregatedDetectionResult;
import de.justsimplenetworks.antivpn.api.ConnectionContext;
import de.justsimplenetworks.antivpn.api.RiskLevel;

import java.util.Objects;

/** Fired when a connection's risk score reaches {@link RiskLevel#HIGH} or {@link RiskLevel#CRITICAL}. */
public final class HighRiskConnectionEvent extends DetectionFlagEvent {

    private final int riskScore;
    private final RiskLevel riskLevel;

    public HighRiskConnectionEvent(ConnectionContext context, AggregatedDetectionResult result,
                                    int riskScore, RiskLevel riskLevel) {
        super(context, result);
        this.riskScore = riskScore;
        this.riskLevel = Objects.requireNonNull(riskLevel, "riskLevel must not be null");
    }

    public int riskScore() {
        return riskScore;
    }

    public RiskLevel riskLevel() {
        return riskLevel;
    }
}
