package de.justsimplenetworks.antivpn.api;

/**
 * The client platform a connecting player is using. Populated from the
 * handshake / login packet when the integration layer can determine it
 * (e.g. via a Floodgate/Geyser marker prefix on Bedrock). Detection logic
 * must never assume {@link #JAVA} when the platform could not be determined;
 * use {@link #UNKNOWN} instead so downstream consumers (such as the future
 * NetworkAntiMultiAccount system) can apply platform-specific limits correctly.
 */
public enum Platform {
    JAVA,
    BEDROCK,
    UNKNOWN
}
