package de.justsimplenetworks.antivpn.security;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;
import java.util.HexFormat;

/**
 * Produces a stable, salted SHA-256 hash of an IP address so that other
 * systems (most importantly the future NetworkAntiMultiAccount project) can
 * correlate connections from the same address without this plugin needing
 * to retain the raw IP (see {@code docs/PRIVACY.md}).
 * <p>
 * The salt is operator-configured ({@code privacy.hash-salt}) so hashes are
 * not comparable across independently-configured networks, and are not
 * reversible via a plain rainbow table of the IPv4 address space.
 */
public final class IpHasher {

    private final byte[] salt;

    public IpHasher(String salt) {
        if (salt == null || salt.isBlank()) {
            throw new IllegalArgumentException("privacy.hash-salt must be configured with a non-empty value");
        }
        this.salt = salt.getBytes(StandardCharsets.UTF_8);
    }

    public String hash(String ip) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            digest.update(salt);
            digest.update(ip.getBytes(StandardCharsets.UTF_8));
            byte[] result = digest.digest();
            return HexFormat.of().formatHex(result);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is not available on this JVM", e);
        }
    }

    /** Compact, URL-safe form of {@link #hash(String)} for use in cache keys or log lines. */
    public String hashShort(String ip) {
        String full = hash(ip);
        byte[] raw = HexFormat.of().parseHex(full);
        byte[] truncated = new byte[9];
        System.arraycopy(raw, 0, truncated, 0, truncated.length);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(truncated);
    }
}
