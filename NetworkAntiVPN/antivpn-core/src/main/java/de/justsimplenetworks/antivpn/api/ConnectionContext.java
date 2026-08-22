package de.justsimplenetworks.antivpn.api;

import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Everything the engine knows about a single connection attempt at the point
 * a decision needs to be made. Built by the platform integration layer
 * (Velocity/Paper/Purpur/BungeeCord adapters) and passed into
 * {@code core.ConnectionAnalysisService}. This class is intentionally free of
 * any platform type (no Velocity {@code InboundConnection}, no Bukkit
 * {@code Player}) so the core module stays platform-independent.
 *
 * @param playerUuid   the player's UUID, {@code null} only during very early
 *                     pre-login stages where it is not yet known
 * @param playerName   the player's username
 * @param ip           the remote IP address as text (IPv4 or IPv6)
 * @param platform     {@link Platform#JAVA}, {@link Platform#BEDROCK} or {@link Platform#UNKNOWN}
 * @param serverName   the backend server the player is connecting to, or
 *                     {@code null} on the proxy stage before routing is known
 * @param serverGroup  the logical server group (e.g. "lobby", "survival"),
 *                     used for server-group-specific country rules
 * @param stage        which pipeline stage produced this context
 * @param timestamp    when the connection attempt was observed
 */
public record ConnectionContext(
        UUID playerUuid,
        String playerName,
        String ip,
        Platform platform,
        String serverName,
        String serverGroup,
        Stage stage,
        Instant timestamp
) {

    public ConnectionContext {
        Objects.requireNonNull(ip, "ip must not be null");
        Objects.requireNonNull(playerName, "playerName must not be null");
        Objects.requireNonNull(platform, "platform must not be null");
        Objects.requireNonNull(stage, "stage must not be null");
        Objects.requireNonNull(timestamp, "timestamp must not be null");
    }

    public Optional<UUID> uuid() {
        return Optional.ofNullable(playerUuid);
    }

    public Optional<String> server() {
        return Optional.ofNullable(serverName);
    }

    /** Which point in the connection pipeline this context was captured at. */
    public enum Stage {
        /** Velocity/Bungee proxy pre-login / login stage - the primary, earliest check point. */
        PROXY_LOGIN,
        /** Paper/Purpur backend join - only re-checked if no proxy result was forwarded. */
        BACKEND_JOIN,
        /** Manual staff-triggered check (e.g. {@code /antivpn check}). */
        MANUAL
    }
}
