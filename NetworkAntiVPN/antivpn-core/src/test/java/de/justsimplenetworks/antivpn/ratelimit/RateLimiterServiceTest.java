package de.justsimplenetworks.antivpn.ratelimit;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class RateLimiterServiceTest {

    private RateLimiterService service;

    @AfterEach
    void tearDown() {
        if (service != null) service.shutdown();
    }

    @Test
    void allowsUpToLimit() {
        service = new RateLimiterService(true, 3, Duration.ofSeconds(10), 0, Duration.ofSeconds(10));
        assertTrue(service.tryAcquireForIp("1.2.3.4"));
        assertTrue(service.tryAcquireForIp("1.2.3.4"));
        assertTrue(service.tryAcquireForIp("1.2.3.4"));
    }

    @Test
    void blocksOverLimit() {
        service = new RateLimiterService(true, 2, Duration.ofSeconds(10), 0, Duration.ofSeconds(10));
        assertTrue(service.tryAcquireForIp("1.2.3.4"));
        assertTrue(service.tryAcquireForIp("1.2.3.4"));
        assertFalse(service.tryAcquireForIp("1.2.3.4"));
    }

    @Test
    void disabledLimiterAlwaysAllows() {
        service = new RateLimiterService(false, 1, Duration.ofSeconds(10), 0, Duration.ofSeconds(10));
        for (int i = 0; i < 10; i++) {
            assertTrue(service.tryAcquireForIp("1.2.3.4"));
        }
    }

    @Test
    void differentIpsAreTrackedIndependently() {
        service = new RateLimiterService(true, 1, Duration.ofSeconds(10), 0, Duration.ofSeconds(10));
        assertTrue(service.tryAcquireForIp("1.2.3.4"));
        assertTrue(service.tryAcquireForIp("5.6.7.8"));
    }

    @Test
    void perPlayerRateLimitIsEnforced() {
        service = new RateLimiterService(true, 0, Duration.ofSeconds(10), 1, Duration.ofSeconds(10));
        UUID uuid = UUID.randomUUID();
        assertTrue(service.tryAcquireForPlayer(uuid));
        assertFalse(service.tryAcquireForPlayer(uuid));
    }

    @Test
    void ipv6AddressesAreRateLimitedLikeIpv4() {
        service = new RateLimiterService(true, 1, Duration.ofSeconds(10), 0, Duration.ofSeconds(10));
        assertTrue(service.tryAcquireForIp("2001:db8::1"));
        assertFalse(service.tryAcquireForIp("2001:db8::1"));
    }
}
