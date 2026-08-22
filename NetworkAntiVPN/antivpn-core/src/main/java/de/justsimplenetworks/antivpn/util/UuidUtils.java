package de.justsimplenetworks.antivpn.util;

import java.util.UUID;

/** Helpers for parsing Minecraft UUIDs, which are sometimes given without dashes ("Mojang trimmed" form). */
public final class UuidUtils {

    private UuidUtils() {
    }

    /** Parses a UUID with or without dashes. Returns {@code null} (not an exception) if the text is not a UUID. */
    public static UUID parseOrNull(String text) {
        if (text == null) {
            return null;
        }
        String trimmed = text.trim();
        try {
            if (trimmed.length() == 32 && !trimmed.contains("-")) {
                return UUID.fromString(
                        trimmed.replaceFirst(
                                "(\\w{8})(\\w{4})(\\w{4})(\\w{4})(\\w{12})", "$1-$2-$3-$4-$5"));
            }
            return UUID.fromString(trimmed);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
