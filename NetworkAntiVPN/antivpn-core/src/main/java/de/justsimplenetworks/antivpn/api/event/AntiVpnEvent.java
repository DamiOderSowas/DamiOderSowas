package de.justsimplenetworks.antivpn.api.event;

import de.justsimplenetworks.antivpn.api.ConnectionContext;

import java.time.Instant;
import java.util.Objects;

/**
 * Base type for every event NetworkAntiVPN publishes on its internal
 * {@link EventBus}. Platform adapters may bridge these onto native
 * Bukkit/Velocity events so other JustSimpleNetworks plugins (and later
 * NetworkCore/NetworkAntiMultiAccount) can listen without depending on this
 * module directly.
 */
public abstract class AntiVpnEvent {

    private final ConnectionContext context;
    private final Instant timestamp;

    protected AntiVpnEvent(ConnectionContext context) {
        this.context = Objects.requireNonNull(context, "context must not be null");
        this.timestamp = Instant.now();
    }

    public ConnectionContext context() {
        return context;
    }

    public Instant timestamp() {
        return timestamp;
    }
}
