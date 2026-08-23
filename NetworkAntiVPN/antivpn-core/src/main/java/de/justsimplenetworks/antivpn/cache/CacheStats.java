package de.justsimplenetworks.antivpn.cache;

/** Point-in-time cache statistics, exposed via {@code /antivpn cache}. */
public record CacheStats(long hits, long misses, int size, int maxSize) {

    public double hitRate() {
        long total = hits + misses;
        return total == 0 ? 0.0 : (double) hits / total;
    }
}
