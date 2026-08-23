package de.justsimplenetworks.antivpn.logging;

import de.justsimplenetworks.antivpn.security.LogSanitizer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Thin wrapper around an SLF4J {@link Logger} that runs every {@code String}
 * argument through {@link LogSanitizer} before formatting, so a player name,
 * IP-derived hostname, or provider response value can never be used to forge
 * fake log lines or inject terminal escape sequences. Use this (instead of a
 * raw SLF4J logger) anywhere a log statement includes player- or
 * provider-supplied text.
 * <p>
 * Velocity injects an SLF4J {@link Logger} directly, so no adapter is needed
 * there. Paper/Purpur and BungeeCord route {@code java.util.logging} through
 * an SLF4J bridge at startup (see the respective integration modules) so
 * this same type can be used on every platform.
 */
public final class SafeLogger {

    private final Logger delegate;

    private SafeLogger(Logger delegate) {
        this.delegate = delegate;
    }

    public static SafeLogger of(Class<?> owner) {
        return new SafeLogger(LoggerFactory.getLogger(owner));
    }

    public static SafeLogger wrap(Logger delegate) {
        return new SafeLogger(delegate);
    }

    public void debug(String message, Object... args) {
        if (delegate.isDebugEnabled()) {
            delegate.debug(message, sanitize(args));
        }
    }

    public void info(String message, Object... args) {
        delegate.info(message, sanitize(args));
    }

    public void warn(String message, Object... args) {
        delegate.warn(message, sanitize(args));
    }

    public void error(String message, Object... args) {
        delegate.error(message, sanitize(args));
    }

    private Object[] sanitize(Object[] args) {
        Object[] result = new Object[args.length];
        for (int i = 0; i < args.length; i++) {
            result[i] = args[i] instanceof String s ? LogSanitizer.sanitize(s) : args[i];
        }
        return result;
    }
}
