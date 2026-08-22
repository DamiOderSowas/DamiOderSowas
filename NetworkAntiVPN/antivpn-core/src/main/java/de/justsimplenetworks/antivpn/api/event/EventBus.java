package de.justsimplenetworks.antivpn.api.event;

import java.util.function.Consumer;

/**
 * Minimal, dependency-free publish/subscribe bus for {@link AntiVpnEvent}s.
 * Kept intentionally small so the core module never needs a platform event
 * bus (Bukkit's {@code PluginManager}, Velocity's {@code EventManager}, ...).
 * Platform integrations subscribe here once at startup and re-fire the
 * events on their native event bus.
 */
public interface EventBus {

    <T extends AntiVpnEvent> void subscribe(Class<T> eventType, Consumer<T> listener);

    void publish(AntiVpnEvent event);
}
