package de.justsimplenetworks.antivpn.cache;

import java.time.Duration;
import java.time.Instant;
import java.util.Iterator;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Generic thread-safe, bounded, TTL-based in-memory cache. Shared engine
 * behind {@link InMemoryDetectionCache} and {@link InMemoryProviderResultCache}.
 * Bounded size prevents unbounded memory growth (a hard security requirement
 * for a plugin that caches data keyed by attacker-influenced IP addresses).
 */
public final class TtlCache<K, V> {

    private final ConcurrentHashMap<K, CacheEntry<V>> store = new ConcurrentHashMap<>();
    private final int maxEntries;
    private final AtomicLong hits = new AtomicLong();
    private final AtomicLong misses = new AtomicLong();
    private final ScheduledExecutorService cleaner;

    public TtlCache(int maxEntries, Duration cleanupInterval) {
        this.maxEntries = Math.max(1, maxEntries);
        this.cleaner = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "antivpn-cache-cleanup");
            t.setDaemon(true);
            return t;
        });
        long intervalMillis = Math.max(1000, cleanupInterval.toMillis());
        this.cleaner.scheduleAtFixedRate(this::evictExpired, intervalMillis, intervalMillis, TimeUnit.MILLISECONDS);
    }

    public Optional<V> get(K key) {
        CacheEntry<V> entry = store.get(key);
        if (entry == null || entry.isExpired()) {
            if (entry != null) {
                store.remove(key, entry);
            }
            misses.incrementAndGet();
            return Optional.empty();
        }
        hits.incrementAndGet();
        return Optional.of(entry.value());
    }

    public void put(K key, V value, Duration ttl) {
        if (!store.containsKey(key) && store.size() >= maxEntries) {
            evictOne();
        }
        Instant now = Instant.now();
        store.put(key, new CacheEntry<>(value, now, now.plus(ttl)));
    }

    public void invalidate(K key) {
        store.remove(key);
    }

    public void invalidateAll() {
        store.clear();
    }

    public int size() {
        return store.size();
    }

    public int maxEntries() {
        return maxEntries;
    }

    public long hitCount() {
        return hits.get();
    }

    public long missCount() {
        return misses.get();
    }

    public void shutdown() {
        cleaner.shutdown();
    }

    private void evictExpired() {
        store.entrySet().removeIf(entry -> entry.getValue().isExpired());
    }

    private void evictOne() {
        Iterator<Map.Entry<K, CacheEntry<V>>> it = store.entrySet().iterator();
        if (it.hasNext()) {
            it.next();
            it.remove();
        }
    }
}
