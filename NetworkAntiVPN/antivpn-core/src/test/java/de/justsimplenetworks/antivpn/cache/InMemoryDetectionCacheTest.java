package de.justsimplenetworks.antivpn.cache;

import de.justsimplenetworks.antivpn.api.AggregatedDetectionResult;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class InMemoryDetectionCacheTest {

    private InMemoryDetectionCache cache;

    private AggregatedDetectionResult sample(String ip) {
        return new AggregatedDetectionResult(ip, Instant.now(), List.of(), false, false, false, false,
                false, false, false, "DE", "EU", 12345, "Example ISP", "Example Org", 1.0, 1.0, List.of(), false);
    }

    @AfterEach
    void tearDown() {
        if (cache != null) {
            cache.shutdown();
        }
    }

    @Test
    void missOnEmptyCache() {
        cache = new InMemoryDetectionCache(100, Duration.ofSeconds(30));
        assertTrue(cache.get("1.2.3.4").isEmpty());
        assertEquals(1, cache.stats().misses());
    }

    @Test
    void hitAfterPut() {
        cache = new InMemoryDetectionCache(100, Duration.ofSeconds(30));
        AggregatedDetectionResult result = sample("1.2.3.4");
        cache.put("1.2.3.4", result, Duration.ofSeconds(60));
        assertTrue(cache.get("1.2.3.4").isPresent());
        assertEquals(1, cache.stats().hits());
    }

    @Test
    void expiresAfterTtl() throws InterruptedException {
        cache = new InMemoryDetectionCache(100, Duration.ofSeconds(30));
        cache.put("1.2.3.4", sample("1.2.3.4"), Duration.ofMillis(50));
        Thread.sleep(150);
        assertTrue(cache.get("1.2.3.4").isEmpty());
    }

    @Test
    void invalidateRemovesEntry() {
        cache = new InMemoryDetectionCache(100, Duration.ofSeconds(30));
        cache.put("1.2.3.4", sample("1.2.3.4"), Duration.ofSeconds(60));
        cache.invalidate("1.2.3.4");
        assertTrue(cache.get("1.2.3.4").isEmpty());
    }

    @Test
    void ipv4AndIpv6AreNormalizedConsistently() {
        cache = new InMemoryDetectionCache(100, Duration.ofSeconds(30));
        cache.put("2001:0db8:0000:0000:0000:0000:0000:0001", sample("2001:db8::1"), Duration.ofSeconds(60));
        assertTrue(cache.get("2001:db8::1").isPresent());
    }

    @Test
    void boundedSizeEvictsWhenFull() {
        cache = new InMemoryDetectionCache(2, Duration.ofSeconds(30));
        cache.put("1.1.1.1", sample("1.1.1.1"), Duration.ofSeconds(60));
        cache.put("2.2.2.2", sample("2.2.2.2"), Duration.ofSeconds(60));
        cache.put("3.3.3.3", sample("3.3.3.3"), Duration.ofSeconds(60));
        assertTrue(cache.stats().size() <= 2);
    }
}
