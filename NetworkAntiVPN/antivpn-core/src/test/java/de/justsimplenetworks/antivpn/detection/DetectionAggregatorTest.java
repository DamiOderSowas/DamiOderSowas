package de.justsimplenetworks.antivpn.detection;

import de.justsimplenetworks.antivpn.api.AggregatedDetectionResult;
import de.justsimplenetworks.antivpn.api.DetectionResult;
import de.justsimplenetworks.antivpn.cache.InMemoryProviderResultCache;
import de.justsimplenetworks.antivpn.providers.DetectionProvider;
import de.justsimplenetworks.antivpn.providers.MockDetectionProvider;
import de.justsimplenetworks.antivpn.providers.ProviderCapability;
import de.justsimplenetworks.antivpn.providers.ProviderConfig;
import de.justsimplenetworks.antivpn.ratelimit.RateLimiterService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static org.junit.jupiter.api.Assertions.*;

class DetectionAggregatorTest {

    private ExecutorService executor;
    private RateLimiterService rateLimiter;

    private DetectionAggregator newAggregator(List<DetectionProvider> providers) {
        executor = Executors.newFixedThreadPool(4);
        rateLimiter = new RateLimiterService(false, 0, Duration.ofSeconds(1), 0, Duration.ofSeconds(1));
        return new DetectionAggregator(providers, new InMemoryProviderResultCache(100, Duration.ofSeconds(30)),
                executor, rateLimiter, Duration.ofSeconds(30), Duration.ofSeconds(2), 0.5);
    }

    @AfterEach
    void tearDown() {
        if (executor != null) executor.shutdownNow();
        if (rateLimiter != null) rateLimiter.shutdown();
    }

    private ProviderConfig config(String name, double weight) {
        return new ProviderConfig(name, true, 1, weight, Duration.ofSeconds(2), 0,
                Set.of(ProviderCapability.VPN), null, null, null, null, null);
    }

    private DetectionProvider vpnProvider(String name, boolean vpn) {
        return new MockDetectionProvider(config(name, 1.0), ip -> CompletableFuture.completedFuture(
                DetectionResult.builder(ip, name).vpn(vpn).confidence(1.0).build()));
    }

    @Test
    void vpnDetectedWhenProviderReportsIt() {
        AggregatedDetectionResult result = newAggregator(List.of(vpnProvider("p1", true)))
                .aggregate("1.2.3.4").join();
        assertTrue(result.vpn());
    }

    @Test
    void vpnNotDetectedWhenProviderReportsClean() {
        AggregatedDetectionResult result = newAggregator(List.of(vpnProvider("p1", false)))
                .aggregate("1.2.3.4").join();
        assertFalse(result.vpn());
    }

    @Test
    void multipleProvidersAgreeingProduceHighAgreement() {
        AggregatedDetectionResult result = newAggregator(List.of(
                vpnProvider("p1", true), vpnProvider("p2", true))).aggregate("1.2.3.4").join();
        assertTrue(result.vpn());
        assertEquals(1.0, result.providerAgreement());
    }

    @Test
    void providersDisagreeingIsReflectedInAgreement() {
        AggregatedDetectionResult result = newAggregator(List.of(
                vpnProvider("p1", true), vpnProvider("p2", false))).aggregate("1.2.3.4").join();
        assertTrue(result.providerAgreement() < 1.0);
    }

    @Test
    void providerTimeoutIsExcludedButDoesNotFailAggregation() {
        // Completes well after the 2s per-provider timeout configured in newAggregator(), but
        // still completes eventually so the test never leaves a permanently blocked thread behind.
        DetectionProvider slow = new MockDetectionProvider(config("slow", 1.0), ip -> CompletableFuture.supplyAsync(() -> {
            try {
                Thread.sleep(4000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            return DetectionResult.builder(ip, "slow").vpn(true).build();
        }));
        DetectionProvider fast = vpnProvider("fast", true);
        AggregatedDetectionResult result = newAggregator(List.of(slow, fast)).aggregate("1.2.3.4").join();
        assertTrue(result.vpn());
        assertEquals(1, result.successfulProviderCount());
        assertEquals(1, result.failedProviderCount());
    }

    @Test
    void providerErrorIsExcludedButDoesNotFailAggregation() {
        DetectionProvider offline = new MockDetectionProvider(config("offline", 1.0),
                ip -> CompletableFuture.completedFuture(DetectionResult.error(ip, "offline", "connection refused")));
        AggregatedDetectionResult result = newAggregator(List.of(offline)).aggregate("1.2.3.4").join();
        assertEquals(0, result.successfulProviderCount());
        assertFalse(result.vpn());
    }

    @Test
    void noProvidersProducesUnknownResult() {
        AggregatedDetectionResult result = newAggregator(List.of()).aggregate("1.2.3.4").join();
        assertEquals(0, result.successfulProviderCount());
        assertFalse(result.isSuspicious());
    }

    @Test
    void allProvidersOfflineProducesUnknownResult() {
        DetectionProvider p1 = new MockDetectionProvider(config("p1", 1.0),
                ip -> CompletableFuture.completedFuture(DetectionResult.error(ip, "p1", "offline")));
        DetectionProvider p2 = new MockDetectionProvider(config("p2", 1.0),
                ip -> CompletableFuture.completedFuture(DetectionResult.error(ip, "p2", "offline")));
        AggregatedDetectionResult result = newAggregator(List.of(p1, p2)).aggregate("1.2.3.4").join();
        assertEquals(0, result.successfulProviderCount());
    }
}
