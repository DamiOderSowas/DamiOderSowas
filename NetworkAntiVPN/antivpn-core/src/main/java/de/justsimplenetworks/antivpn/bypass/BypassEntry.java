package de.justsimplenetworks.antivpn.bypass;

import java.time.Instant;
import java.util.UUID;

/**
 * A time-limited exemption from all further checks for one player, created
 * via {@code /antivpn bypass <player> <duration>}.
 *
 * @param uuid       the player's UUID
 * @param ip         the IP the bypass was granted for (informational / audit only;
 *                   the bypass is keyed by UUID, not IP)
 * @param reason     why the bypass was granted
 * @param createdBy  staff member (or "console") who granted it
 * @param createdAt  when it was granted
 * @param expiresAt  when it automatically expires
 */
public record BypassEntry(UUID uuid, String ip, String reason, String createdBy, Instant createdAt, Instant expiresAt) {

    public boolean isExpired() {
        return Instant.now().isAfter(expiresAt);
    }
}
