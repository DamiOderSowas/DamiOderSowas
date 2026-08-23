package de.justsimplenetworks.antivpn.bypass;

import com.google.gson.reflect.TypeToken;
import de.justsimplenetworks.antivpn.util.PersistenceIO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Manages temporary per-player bypasses (see {@code /antivpn bypass}).
 * Entries are persisted to {@code <data>/bypass.json} so they survive a
 * restart, and a background task removes expired entries automatically.
 */
public final class BypassService {

    private static final Logger LOGGER = LoggerFactory.getLogger(BypassService.class);
    private static final TypeToken<List<BypassEntry>> LIST_TYPE = new TypeToken<>() {};

    private final Path dataFile;
    private final ConcurrentHashMap<UUID, BypassEntry> entries = new ConcurrentHashMap<>();
    private final ScheduledExecutorService cleaner;

    public BypassService(Path dataFile) {
        this.dataFile = dataFile;
        for (BypassEntry entry : PersistenceIO.loadList(dataFile, LIST_TYPE)) {
            if (!entry.isExpired()) {
                entries.put(entry.uuid(), entry);
            }
        }
        this.cleaner = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "antivpn-bypass-cleanup");
            t.setDaemon(true);
            return t;
        });
        cleaner.scheduleAtFixedRate(this::evictExpired, 30, 30, TimeUnit.SECONDS);
    }

    public void grant(UUID uuid, String ip, Duration duration, String reason, String createdBy) {
        Instant now = Instant.now();
        BypassEntry entry = new BypassEntry(uuid, ip, reason, createdBy, now, now.plus(duration));
        entries.put(uuid, entry);
        persist();
    }

    public boolean isActive(UUID uuid) {
        BypassEntry entry = entries.get(uuid);
        if (entry == null) {
            return false;
        }
        if (entry.isExpired()) {
            entries.remove(uuid, entry);
            persist();
            return false;
        }
        return true;
    }

    public Optional<BypassEntry> get(UUID uuid) {
        return Optional.ofNullable(entries.get(uuid)).filter(e -> !e.isExpired());
    }

    public void revoke(UUID uuid) {
        if (entries.remove(uuid) != null) {
            persist();
        }
    }

    public List<BypassEntry> listActive() {
        return entries.values().stream().filter(e -> !e.isExpired()).toList();
    }

    private void evictExpired() {
        boolean changed = entries.values().removeIf(BypassEntry::isExpired);
        if (changed) {
            persist();
        }
    }

    private void persist() {
        PersistenceIO.saveList(dataFile, listActive());
    }

    public void shutdown() {
        cleaner.shutdown();
        LOGGER.debug("BypassService stopped");
    }
}
