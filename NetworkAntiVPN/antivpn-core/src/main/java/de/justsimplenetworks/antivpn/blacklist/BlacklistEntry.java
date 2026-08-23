package de.justsimplenetworks.antivpn.blacklist;

import de.justsimplenetworks.antivpn.util.EntrySource;

import java.time.Instant;

/**
 * One blacklist entry. {@link #expiresAt()} is {@code null} for a permanent
 * entry. Blacklist matches take priority over everything except an active
 * whitelist/bypass match - see {@code core.AntiVpnEngine}.
 *
 * @param type      what {@link #value()} represents (IP text, CIDR text, player UUID text, or ASN number as text)
 * @param value     the entry's value
 * @param reason    why this entry exists
 * @param addedBy   staff member, "console", or "config"
 * @param addedAt   when the entry was created
 * @param expiresAt when it automatically expires, or {@code null} for permanent
 * @param source    whether this entry came from {@code config.yml} or a command
 */
public record BlacklistEntry(
        BlacklistEntryType type,
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
