package de.justsimplenetworks.antivpn.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class IpAddressUtilsTest {

    @Test
    void recognizesValidIpv4() {
        assertTrue(IpAddressUtils.isLiteralIpAddress("192.168.1.1"));
        assertTrue(IpAddressUtils.isLiteralIpAddress("8.8.8.8"));
        assertTrue(IpAddressUtils.isLiteralIpAddress("255.255.255.255"));
    }

    @Test
    void rejectsInvalidIpv4() {
        assertFalse(IpAddressUtils.isLiteralIpAddress("256.1.1.1"));
        assertFalse(IpAddressUtils.isLiteralIpAddress("1.2.3"));
        assertFalse(IpAddressUtils.isLiteralIpAddress("not-an-ip"));
        assertFalse(IpAddressUtils.isLiteralIpAddress("localhost"));
    }

    @Test
    void recognizesValidIpv6() {
        assertTrue(IpAddressUtils.isLiteralIpAddress("2001:db8::1"));
        assertTrue(IpAddressUtils.isLiteralIpAddress("::1"));
        assertTrue(IpAddressUtils.isLiteralIpAddress("fe80::1"));
        assertTrue(IpAddressUtils.isLiteralIpAddress("2001:0db8:0000:0000:0000:0000:0000:0001"));
    }

    @Test
    void matchesIpv4Cidr() {
        assertTrue(IpAddressUtils.isInCidr("192.168.5.10", "192.168.0.0/16"));
        assertFalse(IpAddressUtils.isInCidr("192.169.5.10", "192.168.0.0/16"));
        assertTrue(IpAddressUtils.isInCidr("10.0.0.1", "10.0.0.0/8"));
    }

    @Test
    void matchesIpv6Cidr() {
        assertTrue(IpAddressUtils.isInCidr("2001:db8::1", "2001:db8::/32"));
        assertFalse(IpAddressUtils.isInCidr("2001:db9::1", "2001:db8::/32"));
    }

    @Test
    void ipv4AndIpv6NeverCrossMatchCidr() {
        assertFalse(IpAddressUtils.isInCidr("2001:db8::1", "192.168.0.0/16"));
    }

    @Test
    void recognizesPrivateAndReservedAddresses() {
        assertTrue(IpAddressUtils.isPrivateOrReserved("127.0.0.1"));
        assertTrue(IpAddressUtils.isPrivateOrReserved("10.0.0.1"));
        assertTrue(IpAddressUtils.isPrivateOrReserved("192.168.1.1"));
        assertTrue(IpAddressUtils.isPrivateOrReserved("169.254.1.1"));
        assertFalse(IpAddressUtils.isPrivateOrReserved("8.8.8.8"));
    }

    @Test
    void normalizeIsStable() {
        assertEquals(IpAddressUtils.normalize("192.168.1.1"), IpAddressUtils.normalize("192.168.1.1"));
    }
}
