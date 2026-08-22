package de.justsimplenetworks.antivpn.whitelist;

import com.google.gson.reflect.TypeToken;
import de.justsimplenetworks.antivpn.util.EntrySource;
import de.justsimplenetworks.antivpn.util.IpAddressUtils;
import de.justsimplenetworks.antivpn.util.PersistenceIO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * IPv4/IPv6/CIDR/player whitelist. Whitelist is always evaluated before any
 * block decision (see {@code core.AntiVpnEngine}) - a match short-circuits
 * the entire pipeline with {@code Decision.BYPASS}.
 * <p>
 * Combines three sources: entries loaded once from {@code config.yml}
 * (refreshed wholesale on {@link #reloadConfigEntries}), entries added at
 * runtime via commands (persisted to {@code <data>/whitelist.json}), and
 * temporary entries which expire automatically.
 */
public final class WhitelistService {

    private static final Logger LOGGER = LoggerFactory.getLogger(WhitelistService.class);
    private static final TypeToken<List<WhitelistEntry>> LIST_TYPE = new TypeToken<>() {};

    private final Path dataFile;
    private final List<WhitelistEntry> entries = new CopyOnWriteArrayList<>();
    private final ScheduledExecutorService cleaner;

    public WhitelistService(Path dataFile) {
        this.dataFile = dataFile;
        for (WhitelistEntry entry : PersistenceIO.loadList(dataFile, LIST_TYPE)) {
            if (!entry.isExpired()) {
                entries.add(entry);
            }
        }
        this.cleaner = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "antivpn-whitelist-cleanup");
            t.setDaemon(true);
            return t;
        });
        cleaner.scheduleAtFixedRate(this::evictExpired, 30, 30, TimeUnit.SECONDS);
    }

    /** Replaces all {@link EntrySource#CONFIG}-sourced entries with the given lists from {@code config.yml}. */
    public void reloadConfigEntries(List<String> ips, List<String> cidrs, List<String> players) {
        entries.removeIf(e -> e.source() == EntrySource.CONFIG);
        Instant now = Instant.now();
        for (String ip : ips) {
            entries.add(new WhitelistEntry(WhitelistEntryType.IP, IpAddressUtils.normalize(ip),
                    "configured in config.yml", "config", now, null, EntrySource.CONFIG));
        }
        for (String cidr : cidrs) {
            entries.add(new WhitelistEntry(WhitelistEntryType.CIDR, cidr,
                    "configured in config.yml", "config", now, null, EntrySource.CONFIG));
        }
        for (String player : players) {
            entries.add(new WhitelistEntry(WhitelistEntryType.PLAYER, normalizePlayerValue(player),
                    "configured in config.yml", "config", now, null, EntrySource.CONFIG));
        }
    }

    private String normalizePlayerValue(String player) {
        return player.trim();
    }

    /** Checks whether the given connection matches any whitelist entry. */
    public Optional<WhitelistEntry> check(String ip, UUID uuid, String playerName) {
        String normalizedIp = IpAddressUtils.isLiteralIpAddress(ip) ? IpAddressUtils.normalize(ip) : ip;
        for (WhitelistEntry entry : entries) {
            if (entry.isExpired()) {
                continue;
            }
            boolean matches = switch (entry.type()) {
                case IP -> entry.value().equals(normalizedIp);
                case CIDR -> isLiteral(normalizedIp) && IpAddressUtils.isInCidr(normalizedIp, entry.value());
                case PLAYER -> matchesPlayer(entry.value(), uuid, playerName);
            };
            if (matches) {
                return Optional.of(entry);
            }
        }
        return Optional.empty();
    }

    private boolean isLiteral(String ip) {
        return IpAddressUtils.isLiteralIpAddress(ip);
    }

    private boolean matchesPlayer(String value, UUID uuid, String playerName) {
        if (uuid != null && value.equalsIgnoreCase(uuid.toString())) {
            return true;
        }
        return playerName != null && value.equalsIgnoreCase(playerName);
    }

    public void addIp(String ip, Duration duration, String reason, String addedBy) {
        add(new WhitelistEntry(WhitelistEntryType.IP, IpAddressUtils.normalize(ip), reason, addedBy,
                Instant.now(), expiryOf(duration), EntrySource.COMMAND));
    }

    public void addCidr(String cidr, Duration duration, String reason, String addedBy) {
        add(new WhitelistEntry(WhitelistEntryType.CIDR, cidr, reason, addedBy,
                Instant.now(), expiryOf(duration), EntrySource.COMMAND));
    }

    public void addPlayer(UUID uuid, Duration duration, String reason, String addedBy) {
        add(new WhitelistEntry(WhitelistEntryType.PLAYER, uuid.toString(), reason, addedBy,
                Instant.now(), expiryOf(duration), EntrySource.COMMAND));
    }

    private Instant expiryOf(Duration duration) {
        return duration == null ? null : Instant.now().plus(duration);
    }

    private void add(WhitelistEntry entry) {
        entries.removeIf(e -> e.source() == EntrySource.COMMAND && e.type() == entry.type()
                && e.value().equalsIgnoreCase(entry.value()));
        entries.add(entry);
        persist();
    }

    public boolean remove(WhitelistEntryType type, String value) {
        boolean removed = entries.removeIf(e -> e.source() == EntrySource.COMMAND
                && e.type() == type && e.value().equalsIgnoreCase(value));
        if (removed) {
            persist();
        }
        return removed;
    }

    public List<WhitelistEntry> list() {
        return List.copyOf(entries);
    }

    private void evictExpired() {
        boolean changed = entries.removeIf(WhitelistEntry::isExpired);
        if (changed) {
            persist();
        }
    }

    private void persist() {
        List<WhitelistEntry> commandEntries = new ArrayList<>();
        for (WhitelistEntry entry : entries) {
            if (entry.source() == EntrySource.COMMAND) {
                commandEntries.add(entry);
            }
        }
        PersistenceIO.saveList(dataFile, commandEntries);
    }

    public void shutdown() {
        cleaner.shutdown();
        LOGGER.debug("WhitelistService stopped");
    }
}
