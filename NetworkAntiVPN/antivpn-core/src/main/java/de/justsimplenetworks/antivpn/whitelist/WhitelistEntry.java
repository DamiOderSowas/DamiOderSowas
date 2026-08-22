package de.justsimplenetworks.antivpn.whitelist;

import de.justsimplenetworks.antivpn.util.EntrySource;

import java.time.Instant;

/**
 * One whitelist entry. {@link #expiresAt()} is {@code null} for a permanent
 * entry (config-defined, or added without a duration via
 * {@code /antivpn whitelist add}/{@code /antivpn trust}).
 *
 * @param type      what {@link #value()} represents
 * @param value     the IP text, CIDR text, or player UUID text
 * @param reason    why this entry exists
 * @param addedBy   staff member, "console", or "config"
 * @param addedAt   when the entry was created
 * @param expiresAt when it automatically expires, or {@code null} for permanent
 * @param source    whether this entry came from {@code config.yml} or a command
 */
public record WhitelistEntry(
        WhitelistEntryType type,
        String value,
        String reason,
        String addedBy,
        Instant addedAt,
        Instant expiresAt,
        EntrySource source
) {
    public boolean isExpired() {
        return expiresAt != null && Instant.now().isAfter(expiresAt);
    }

    public boolean isTemporary() {
        return expiresAt != null;
    }
}
