package de.justsimplenetworks.antivpn.util;

/**
 * Where a whitelist/blacklist entry came from. Lets a {@code reload()} safely
 * refresh {@link #CONFIG}-sourced entries from {@code config.yml} without
 * touching entries added at runtime via {@link #COMMAND}.
 */
public enum EntrySource {
    CONFIG,
    COMMAND
}
