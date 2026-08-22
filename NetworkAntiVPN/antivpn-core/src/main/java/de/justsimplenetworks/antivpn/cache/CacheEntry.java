package de.justsimplenetworks.antivpn.cache;

import java.time.Instant;

/** A cached value together with when it was created and when it expires. */
public record CacheEntry<V>(V value, Instant createdAt, Instant expiresAt) {

    public boolean isExpired() {
        return Instant.now().isAfter(expiresAt);
    }
}
