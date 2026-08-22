package de.justsimplenetworks.antivpn.ratelimit;

import de.justsimplenetworks.antivpn.util.IpAddressUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Protects the proxy/backend from connection spam by limiting how many
 * connection attempts (and, separately, provider requests) are allowed per
 * IP, per player UUID and per provider within a trailing time window.
 * IPv4 and IPv6 addresses are each rate limited individually as literal
 * strings (no /64 aggregation) - see {@code docs/RATE-LIMIT.md} for why
 * that tradeoff was chosen and how to add subnet-based limiting yourself.
 */
public final class RateLimiterService {

    private static final Logger LOGGER = LoggerFactory.getLogger(RateLimiterService.class);
    private static final Duration STALE_THRESHOLD = Duration.ofMinutes(10);

    private final ConcurrentHashMap<String, SlidingWindowCounter> ipCounters = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<UUID, SlidingWindowCounter> uuidCounters = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, SlidingWindowCounter> providerCounters = new ConcurrentHashMap<>();

    private final boolean enabled;
    private final int maxConnectionsPerIp;
    private final Duration ipWindow;
    private final int maxConnectionsPerPlayer;
    private final Duration playerWindow;
    private final ScheduledExecutorService cleaner;

    public RateLimiterService(boolean enabled, int maxConnectionsPerIp, Duration ipWindow,
                               int maxConnectionsPerPlayer, Duration playerWindow) {
        this.enabled = enabled;
        this.maxConnectionsPerIp = maxConnectionsPerIp;
        this.ipWindow = ipWindow;
        this.maxConnectionsPerPlayer = maxConnectionsPerPlayer;
        this.playerWindow = playerWindow;
        this.cleaner = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "antivpn-ratelimit-cleanup");
            t.setDaemon(true);
            return t;
        });
        cleaner.scheduleAtFixedRate(this::evictStale, 5, 5, TimeUnit.MINUTES);
    }

    /** Whether a new connection attempt from this IP is allowed right now. */
    public boolean tryAcquireForIp(String ip) {
        if (!enabled || maxConnectionsPerIp <= 0) {
            return true;
        }
        String key = IpAddressUtils.isLiteralIpAddress(ip) ? IpAddressUtils.normalize(ip) : ip;
        return ipCounters.computeIfAbsent(key, k -> new SlidingWindowCounter(maxConnectionsPerIp, ipWindow))
                .tryRecord();
    }

    /** Whether a new connection attempt from this player is allowed right now. */
    public boolean tryAcquireForPlayer(UUID uuid) {
        if (!enabled || maxConnectionsPerPlayer <= 0 || uuid == null) {
            return true;
        }
        return uuidCounters.computeIfAbsent(uuid, k -> new SlidingWindowCounter(maxConnectionsPerPlayer, playerWindow))
                .tryRecord();
    }

    /** Whether a new outbound request to the given provider is allowed under its own configured rate limit. */
    public boolean tryAcquireForProvider(String providerName, int maxPerMinute) {
        if (maxPerMinute <= 0) {
            return true;
        }
        return providerCounters
                .computeIfAbsent(providerName, k -> new SlidingWindowCounter(maxPerMinute, Duration.ofMinutes(1)))
                .tryRecord();
    }

    private void evictStale() {
        ipCounters.entrySet().removeIf(e -> e.getValue().isStale(STALE_THRESHOLD));
        uuidCounters.entrySet().removeIf(e -> e.getValue().isStale(STALE_THRESHOLD));
        providerCounters.entrySet().removeIf(e -> e.getValue().isStale(STALE_THRESHOLD));
    }

    public void shutdown() {
        cleaner.shutdown();
        LOGGER.debug("RateLimiterService stopped");
    }
}
