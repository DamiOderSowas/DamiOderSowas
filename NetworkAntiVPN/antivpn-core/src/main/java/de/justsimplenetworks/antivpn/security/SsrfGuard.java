package de.justsimplenetworks.antivpn.security;

import de.justsimplenetworks.antivpn.util.IpAddressUtils;

import java.net.InetAddress;
import java.net.URI;
import java.net.UnknownHostException;

/**
 * Validates outbound provider request URLs before they are ever opened, to
 * defend against SSRF via a misconfigured or malicious {@code urlTemplate}:
 * <ul>
 *   <li>only {@code https} is allowed (configurable providers must not send
 *       API keys in cleartext, and this also blocks {@code file:}/{@code gopher:}
 *       style scheme abuse);</li>
 *   <li>the host must not literally be a private/loopback/link-local address;</li>
 *   <li>a hostname host is resolved and every resolved address is checked
 *       too, to catch DNS-rebinding-style redirects toward internal infrastructure.</li>
 * </ul>
 * This check runs once when a {@code ConfigurableHttpDetectionProvider} is
 * built (template validation) and again for every outgoing request (host
 * validation), since DNS answers can change between the two points in time.
 */
public final class SsrfGuard {

    private SsrfGuard() {
    }

    /**
     * @throws SsrfViolationException if the URI is not safe to request
     */
    public static void validate(URI uri) {
        String scheme = uri.getScheme();
        if (scheme == null || !scheme.equalsIgnoreCase("https")) {
            throw new SsrfViolationException("Only https:// URLs are allowed, got: " + scheme);
        }
        String host = uri.getHost();
        if (host == null || host.isBlank()) {
            throw new SsrfViolationException("URL has no host: " + uri);
        }
        if (IpAddressUtils.isLiteralIpAddress(host)) {
            if (IpAddressUtils.isPrivateOrReserved(host)) {
                throw new SsrfViolationException("Refusing to contact a private/reserved address: " + host);
            }
            return;
        }
        InetAddress[] resolved;
        try {
            resolved = InetAddress.getAllByName(host);
        } catch (UnknownHostException e) {
            throw new SsrfViolationException("Could not resolve provider host: " + host);
        }
        for (InetAddress address : resolved) {
            if (IpAddressUtils.isPrivateOrReserved(address.getHostAddress())) {
                throw new SsrfViolationException(
                        "Provider host " + host + " resolves to a private/reserved address: " + address.getHostAddress());
            }
        }
    }

    /** Ensures the {@code {ip}} placeholder is never part of the URL's authority (host/port) component. */
    public static void validateTemplate(String urlTemplate) {
        int authorityEnd = findAuthorityEnd(urlTemplate);
        String authority = urlTemplate.substring(0, authorityEnd);
        if (authority.contains("{ip}")) {
            throw new SsrfViolationException("The {ip} placeholder must not appear in the host/authority: " + urlTemplate);
        }
    }

    private static int findAuthorityEnd(String urlTemplate) {
        int pathStart = urlTemplate.indexOf('/', urlTemplate.indexOf("://") + 3);
        return pathStart < 0 ? urlTemplate.length() : pathStart;
    }
}
