package de.justsimplenetworks.antivpn.api.event;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

/**
 * Thread-safe, synchronous, in-memory {@link EventBus} implementation.
 * Listener exceptions are caught and logged so one broken listener can never
 * break the detection pipeline that published the event.
 */
public final class SimpleEventBus implements EventBus {

    private static final Logger LOGGER = LoggerFactory.getLogger(SimpleEventBus.class);

    private final Map<Class<?>, List<Consumer<AntiVpnEvent>>> listeners = new ConcurrentHashMap<>();

    @Override
    @SuppressWarnings("unchecked")
    public <T extends AntiVpnEvent> void subscribe(Class<T> eventType, Consumer<T> listener) {
        listeners.computeIfAbsent(eventType, k -> new CopyOnWriteArrayList<>())
                .add((Consumer<AntiVpnEvent>) listener);
    }

    @Override
    public void publish(AntiVpnEvent event) {
        List<Consumer<AntiVpnEvent>> forType = listeners.get(event.getClass());
        if (forType == null) {
            return;
        }
        for (Consumer<AntiVpnEvent> listener : forType) {
            try {
                listener.accept(event);
            } catch (RuntimeException e) {
                LOGGER.warn("Listener for {} threw an exception", event.getClass().getSimpleName(), e);
            }
        }
    }
}
