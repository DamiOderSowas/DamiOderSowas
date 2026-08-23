package de.justsimplenetworks.antivpn.blacklist;

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
 * IPv4/IPv6/CIDR/player/ASN blacklist. IP/CIDR/player matches are checked
 * early (before any provider request), giving the blacklist high priority as
 * required by the specification. ASN matches can only be checked once
 * detection has resolved an ASN for the connection, so they are exposed via
 * a separate method called after aggregation.
 */
public final class BlacklistService {

    private static final Logger LOGGER = LoggerFactory.getLogger(BlacklistService.class);
    private static final TypeToken<List<BlacklistEntry>> LIST_TYPE = new TypeToken<>() {};

    private final Path dataFile;
    private final List<BlacklistEntry> entries = new CopyOnWriteArrayList<>();
    private final ScheduledExecutorService cleaner;

    public BlacklistService(Path dataFile) {
        this.dataFile = dataFile;
        for (BlacklistEntry entry : PersistenceIO.loadList(dataFile, LIST_TYPE)) {
            if (!entry.isExpired()) {
                entries.add(entry);
            }
        }
        this.cleaner = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "antivpn-blacklist-cleanup");
            t.setDaemon(true);
            return t;
        });
        cleaner.scheduleAtFixedRate(this::evictExpired, 30, 30, TimeUnit.SECONDS);
    }

    public void reloadConfigEntries(List<String> ips, List<String> cidrs, List<String> players, List<Integer> asns) {
        entries.removeIf(e -> e.source() == EntrySource.CONFIG);
        Instant now = Instant.now();
        for (String ip : ips) {
            entries.add(new BlacklistEntry(BlacklistEntryType.IP, IpAddressUtils.normalize(ip),
                    "configured in config.yml", "config", now, null, EntrySource.CONFIG));
        }
        for (String cidr : cidrs) {
            entries.add(new BlacklistEntry(BlacklistEntryType.CIDR, cidr,
                    "configured in config.yml", "config", now, null, EntrySource.CONFIG));
        }
        for (String player : players) {
            entries.add(new BlacklistEntry(BlacklistEntryType.PLAYER, player.trim(),
                    "configured in config.yml", "config", now, null, EntrySource.CONFIG));
        }
        for (Integer asn : asns) {
            entries.add(new BlacklistEntry(BlacklistEntryType.ASN, String.valueOf(asn),
                    "configured in config.yml", "config", now, null, EntrySource.CONFIG));
        }
    }

    public Optional<BlacklistEntry> check(String ip, UUID uuid, String playerName) {
        String normalizedIp = IpAddressUtils.isLiteralIpAddress(ip) ? IpAddressUtils.normalize(ip) : ip;
        for (BlacklistEntry entry : entries) {
            if (entry.isExpired() || entry.type() == BlacklistEntryType.ASN) {
                continue;
            }
            boolean matches = switch (entry.type()) {
                case IP -> entry.value().equals(normalizedIp);
                case CIDR -> IpAddressUtils.isLiteralIpAddress(normalizedIp)
                        && IpAddressUtils.isInCidr(normalizedIp, entry.value());
                case PLAYER -> (uuid != null && entry.value().equalsIgnoreCase(uuid.toString()))
                        || (playerName != null && entry.value().equalsIgnoreCase(playerName));
                case ASN -> false;
            };
            if (matches) {
                return Optional.of(entry);
            }
        }
        return Optional.empty();
    }

    public Optional<BlacklistEntry> checkAsn(Integer asn) {
        if (asn == null) {
            return Optional.empty();
        }
        String asnText = String.valueOf(asn);
        return entries.stream()
                .filter(e -> !e.isExpired() && e.type() == BlacklistEntryType.ASN && e.value().equals(asnText))
                .findFirst();
    }

    public void addIp(String ip, Duration duration, String reason, String addedBy) {
        add(new BlacklistEntry(BlacklistEntryType.IP, IpAddressUtils.normalize(ip), reason, addedBy,
                Instant.now(), expiryOf(duration), EntrySource.COMMAND));
    }

    public void addCidr(String cidr, Duration duration, String reason, String addedBy) {
        add(new BlacklistEntry(BlacklistEntryType.CIDR, cidr, reason, addedBy,
                Instant.now(), expiryOf(duration), EntrySource.COMMAND));
    }

    public void addPlayer(UUID uuid, Duration duration, String reason, String addedBy) {
        add(new BlacklistEntry(BlacklistEntryType.PLAYER, uuid.toString(), reason, addedBy,
                Instant.now(), expiryOf(duration), EntrySource.COMMAND));
    }

    public void addAsn(int asn, Duration duration, String reason, String addedBy) {
        add(new BlacklistEntry(BlacklistEntryType.ASN, String.valueOf(asn), reason, addedBy,
                Instant.now(), expiryOf(duration), EntrySource.COMMAND));
    }

    private Instant expiryOf(Duration duration) {
        return duration == null ? null : Instant.now().plus(duration);
    }

    private void add(BlacklistEntry entry) {
        entries.removeIf(e -> e.source() == EntrySource.COMMAND && e.type() == entry.type()
                && e.value().equalsIgnoreCase(entry.value()));
        entries.add(entry);
        persist();
    }

    public boolean remove(BlacklistEntryType type, String value) {
        boolean removed = entries.removeIf(e -> e.source() == EntrySource.COMMAND
                && e.type() == type && e.value().equalsIgnoreCase(value));
        if (removed) {
            persist();
        }
        return removed;
    }

    public List<BlacklistEntry> list() {
        return List.copyOf(entries);
    }

    private void evictExpired() {
        boolean changed = entries.removeIf(BlacklistEntry::isExpired);
        if (changed) {
            persist();
        }
    }

    private void persist() {
        List<BlacklistEntry> commandEntries = new ArrayList<>();
        for (BlacklistEntry entry : entries) {
            if (entry.source() == EntrySource.COMMAND) {
                commandEntries.add(entry);
            }
        }
        PersistenceIO.saveList(dataFile, commandEntries);
    }

    public void shutdown() {
        cleaner.shutdown();
        LOGGER.debug("BlacklistService stopped");
    }
}
