package de.justsimplenetworks.antivpn.util;

import java.math.BigInteger;
import java.net.Inet4Address;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * IPv4/IPv6 parsing, validation and CIDR matching utilities.
 * <p>
 * Validity is always checked with pure string/regex logic first
 * ({@link #isLiteralIpAddress(String)}) so that no method in this class ever
 * triggers a DNS lookup for attacker-supplied input - important both for
 * correctness (a hostname is not an IP) and for SSRF hardening (see
 * {@code security.SsrfGuard}). {@link InetAddress#getByName(String)} is only
 * ever invoked after the text has already been proven to be a literal
 * address, which the JDK documents as lookup-free.
 */
public final class IpAddressUtils {

    private IpAddressUtils() {
    }

    private static final Pattern IPV4_PATTERN = Pattern.compile(
            "^(25[0-5]|2[0-4]\\d|1\\d\\d|[1-9]?\\d)(\\.(25[0-5]|2[0-4]\\d|1\\d\\d|[1-9]?\\d)){3}$");

    // Widely used, practically-complete IPv6 literal matcher (covers compressed "::" forms
    // and IPv4-mapped/embedded forms). Intentionally does not accept a zone id (%eth0) so that
    // CIDR math never has to reason about scoped addresses; strip it before validation if needed.
    private static final Pattern IPV6_PATTERN = Pattern.compile(
            "^(" +
                    "([0-9A-Fa-f]{1,4}:){7}[0-9A-Fa-f]{1,4}|" +
                    "([0-9A-Fa-f]{1,4}:){1,7}:|" +
                    "([0-9A-Fa-f]{1,4}:){1,6}:[0-9A-Fa-f]{1,4}|" +
                    "([0-9A-Fa-f]{1,4}:){1,5}(:[0-9A-Fa-f]{1,4}){1,2}|" +
                    "([0-9A-Fa-f]{1,4}:){1,4}(:[0-9A-Fa-f]{1,4}){1,3}|" +
                    "([0-9A-Fa-f]{1,4}:){1,3}(:[0-9A-Fa-f]{1,4}){1,4}|" +
                    "([0-9A-Fa-f]{1,4}:){1,2}(:[0-9A-Fa-f]{1,4}){1,5}|" +
                    "[0-9A-Fa-f]{1,4}:((:[0-9A-Fa-f]{1,4}){1,6})|" +
                    ":((:[0-9A-Fa-f]{1,4}){1,7}|:)|" +
                    "::(ffff(:0{1,4})?:)?((25[0-5]|(2[0-4]|1?[0-9])?[0-9])\\.){3}(25[0-5]|(2[0-4]|1?[0-9])?[0-9])|" +
                    "([0-9A-Fa-f]{1,4}:){1,4}:((25[0-5]|(2[0-4]|1?[0-9])?[0-9])\\.){3}(25[0-5]|(2[0-4]|1?[0-9])?[0-9])" +
                    ")$");

    /** Whether the given text is a literal IPv4 or IPv6 address. Never performs a DNS lookup. */
    public static boolean isLiteralIpAddress(String text) {
        if (text == null || text.isBlank()) {
            return false;
        }
        String candidate = stripZoneAndBrackets(text.trim());
        return IPV4_PATTERN.matcher(candidate).matches() || IPV6_PATTERN.matcher(candidate).matches();
    }

    private static String stripZoneAndBrackets(String text) {
        String s = text;
        if (s.startsWith("[") && s.endsWith("]")) {
            s = s.substring(1, s.length() - 1);
        }
        int percent = s.indexOf('%');
        if (percent >= 0) {
            s = s.substring(0, percent);
        }
        return s;
    }

    /**
     * Parses a proven-literal IP address. Only call this after
     * {@link #isLiteralIpAddress(String)} returned {@code true} (all other
     * public methods in this class do so internally).
     *
     * @throws IllegalArgumentException if the text is not a literal IP address
     */
    public static InetAddress parse(String ip) {
        String candidate = stripZoneAndBrackets(ip.trim());
        if (!isLiteralIpAddress(candidate)) {
            throw new IllegalArgumentException("Not a valid literal IP address: " + ip);
        }
        try {
            return InetAddress.getByName(candidate);
        } catch (UnknownHostException e) {
            // Unreachable: getByName never performs a lookup for a literal address.
            throw new IllegalArgumentException("Not a valid literal IP address: " + ip, e);
        }
    }

    /**
     * Whether {@code ip} falls inside the given CIDR block (e.g. {@code 192.168.0.0/16}
     * or {@code 2001:db8::/32}). IPv4 and IPv6 never match each other's CIDRs.
     */
    public static boolean isInCidr(String ip, String cidr) {
        int slash = cidr.lastIndexOf('/');
        if (slash < 0) {
            throw new IllegalArgumentException("Not a CIDR block: " + cidr);
        }
        String base = cidr.substring(0, slash);
        int prefixLength = Integer.parseInt(cidr.substring(slash + 1));

        InetAddress ipAddr = parse(ip);
        InetAddress baseAddr = parse(base);
        if (ipAddr.getClass() != baseAddr.getClass()) {
            return false;
        }

        int addressBits = ipAddr instanceof Inet4Address ? 32 : 128;
        if (prefixLength < 0 || prefixLength > addressBits) {
            throw new IllegalArgumentException("Invalid prefix length /" + prefixLength + " for " + cidr);
        }
        if (prefixLength == 0) {
            return true;
        }

        BigInteger ipInt = toBigInteger(ipAddr);
        BigInteger baseInt = toBigInteger(baseAddr);
        int shift = addressBits - prefixLength;
        BigInteger fullMask = BigInteger.ONE.shiftLeft(addressBits).subtract(BigInteger.ONE);
        BigInteger mask = fullMask.shiftLeft(shift).and(fullMask);
        return ipInt.and(mask).equals(baseInt.and(mask));
    }

    private static BigInteger toBigInteger(InetAddress address) {
        return new BigInteger(1, address.getAddress());
    }

    /** Whether the given literal address is a private, loopback, link-local or otherwise non-routable address. */
    public static boolean isPrivateOrReserved(String ip) {
        InetAddress address = parse(ip);
        return address.isLoopbackAddress()
                || address.isSiteLocalAddress()
                || address.isLinkLocalAddress()
                || address.isAnyLocalAddress()
                || address.isMulticastAddress()
                || isCarrierGradeNat(address)
                || isIpv4MappedIpv6(address);
    }

    private static boolean isCarrierGradeNat(InetAddress address) {
        return address instanceof Inet4Address && isInCidr(address.getHostAddress(), "100.64.0.0/10");
    }

    private static boolean isIpv4MappedIpv6(InetAddress address) {
        if (!(address instanceof Inet6Address)) {
            return false;
        }
        byte[] bytes = address.getAddress();
        for (int i = 0; i < 10; i++) {
            if (bytes[i] != 0) {
                return false;
            }
        }
        return (bytes[10] & 0xFF) == 0xFF && (bytes[11] & 0xFF) == 0xFF;
    }

    /** Normalizes an IP to its canonical textual form (useful as a stable cache key). */
    public static String normalize(String ip) {
        return parse(ip).getHostAddress().toLowerCase(Locale.ROOT);
    }
}
