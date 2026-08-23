package de.justsimplenetworks.antivpn.security;

/** Thrown by {@link SsrfGuard} when a URL is not safe to request. */
public class SsrfViolationException extends RuntimeException {
    public SsrfViolationException(String message) {
        super(message);
    }
}
