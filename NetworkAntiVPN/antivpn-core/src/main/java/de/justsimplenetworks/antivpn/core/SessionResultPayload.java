package de.justsimplenetworks.antivpn.core;

import de.justsimplenetworks.antivpn.api.AggregatedDetectionResult;
import de.justsimplenetworks.antivpn.api.Decision;
import de.justsimplenetworks.antivpn.api.DecisionReason;
import de.justsimplenetworks.antivpn.api.DecisionResult;
import de.justsimplenetworks.antivpn.api.RiskLevel;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.time.Instant;
import java.util.UUID;

/**
 * Wire format for forwarding a computed {@link DecisionResult} from the
 * proxy (Velocity/BungeeCord) to a backend server (Paper/Purpur) over a
 * plugin messaging channel, so the backend never has to re-run detection
 * for the same session. Deliberately hand-rolled with {@link DataOutputStream}
 * instead of a general serialization library to keep the payload tiny and
 * dependency-free - both sides are always this exact plugin, so there is no
 * cross-version compatibility concern beyond this class itself.
 */
public final class SessionResultPayload {

    private SessionResultPayload() {
    }

    public static byte[] encode(UUID playerUuid, DecisionResult decision) {
        try {
            ByteArrayOutputStream buffer = new ByteArrayOutputStream();
            DataOutputStream out = new DataOutputStream(buffer);
            out.writeLong(playerUuid.getMostSignificantBits());
            out.writeLong(playerUuid.getLeastSignificantBits());
            out.writeUTF(decision.decision().name());
            out.writeUTF(decision.reason().name());
            out.writeInt(decision.riskScore());
            out.writeUTF(decision.riskLevel().name());
            AggregatedDetectionResult aggregated = decision.aggregatedResult();
            out.writeBoolean(aggregated != null);
            if (aggregated != null) {
                out.writeUTF(nullToEmpty(aggregated.country()));
                out.writeUTF(nullToEmpty(aggregated.continent()));
                out.writeInt(aggregated.asn() == null ? -1 : aggregated.asn());
                out.writeBoolean(aggregated.vpn());
                out.writeBoolean(aggregated.proxy());
                out.writeBoolean(aggregated.hosting());
                out.writeBoolean(aggregated.datacenter());
                out.writeBoolean(aggregated.tor());
                out.writeBoolean(aggregated.residentialProxy());
            }
            return buffer.toByteArray();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public static Decoded decode(byte[] data) {
        try {
            DataInputStream in = new DataInputStream(new ByteArrayInputStream(data));
            UUID uuid = new UUID(in.readLong(), in.readLong());
            Decision decision = Decision.valueOf(in.readUTF());
            DecisionReason reason = DecisionReason.valueOf(in.readUTF());
            int riskScore = in.readInt();
            RiskLevel riskLevel = RiskLevel.valueOf(in.readUTF());
            boolean hasAggregated = in.readBoolean();
            String country = null;
            String continent = null;
            Integer asn = null;
            boolean vpn = false, proxy = false, hosting = false, datacenter = false, tor = false, residentialProxy = false;
            if (hasAggregated) {
                country = emptyToNull(in.readUTF());
                continent = emptyToNull(in.readUTF());
                int asnValue = in.readInt();
                asn = asnValue < 0 ? null : asnValue;
                vpn = in.readBoolean();
                proxy = in.readBoolean();
                hosting = in.readBoolean();
                datacenter = in.readBoolean();
                tor = in.readBoolean();
                residentialProxy = in.readBoolean();
            }
            DecisionResult decisionResult = new DecisionResult(decision, reason, riskScore, riskLevel,
                    hasAggregated ? new AggregatedDetectionResult(null, Instant.now(), java.util.List.of(),
                            vpn, proxy, hosting, datacenter, tor, residentialProxy, false,
                            country, continent, asn, null, null, 1.0, 1.0, java.util.List.of(), true) : null,
                    "forwarded", null, Instant.now());
            return new Decoded(uuid, decisionResult);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static String nullToEmpty(String value) {
        return value == null ? "" : value;
    }

    private static String emptyToNull(String value) {
        return value == null || value.isEmpty() ? null : value;
    }

    public record Decoded(UUID playerUuid, DecisionResult decision) {
    }
}
