package de.justsimplenetworks.antivpn.commands;

import de.justsimplenetworks.antivpn.api.*;
import de.justsimplenetworks.antivpn.blacklist.BlacklistEntry;
import de.justsimplenetworks.antivpn.blacklist.BlacklistEntryType;
import de.justsimplenetworks.antivpn.bypass.BypassEntry;
import de.justsimplenetworks.antivpn.cache.CacheStats;
import de.justsimplenetworks.antivpn.config.MessagesConfig;
import de.justsimplenetworks.antivpn.core.AntiVpnContext;
import de.justsimplenetworks.antivpn.metrics.MetricKey;
import de.justsimplenetworks.antivpn.providers.DetectionProvider;
import de.justsimplenetworks.antivpn.providers.ProviderHealthSnapshot;
import de.justsimplenetworks.antivpn.util.DurationUtils;
import de.justsimplenetworks.antivpn.util.IpAddressUtils;
import de.justsimplenetworks.antivpn.util.UuidUtils;
import de.justsimplenetworks.antivpn.whitelist.WhitelistEntry;
import de.justsimplenetworks.antivpn.whitelist.WhitelistEntryType;

import java.time.Duration;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * Implements every {@code /antivpn ...} subcommand from the specification
 * against the platform-agnostic {@link CommandSource}/{@link PlayerLookup}
 * abstractions. Each platform module registers exactly one thin command
 * class per platform that simply forwards into {@link #execute}.
 */
public final class AntiVpnCommandExecutor {

    private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final AntiVpnContext context;
    private final PlayerLookup playerLookup;

    public AntiVpnCommandExecutor(AntiVpnContext context, PlayerLookup playerLookup) {
        this.context = context;
        this.playerLookup = playerLookup;
    }

    public void execute(CommandSource source, String[] args) {
        if (args.length == 0) {
            sendHelp(source);
            return;
        }
        String sub = args[0].toLowerCase(Locale.ROOT);
        switch (sub) {
            case "check" -> handleCheck(source, args);
            case "checkip" -> handleCheckIp(source, args);
            case "inspect" -> handleInspect(source, args);
            case "debug" -> handleDebug(source, args);
            case "reload" -> handleReload(source);
            case "status" -> handleStatus(source);
            case "providers" -> handleProviders(source);
            case "cache" -> handleCache(source, args);
            case "whitelist" -> handleWhitelist(source, args);
            case "blacklist" -> handleBlacklist(source, args);
            case "bypass" -> handleBypass(source, args);
            case "trust" -> handleTrust(source, args, true);
            case "untrust" -> handleTrust(source, args, false);
            default -> sendHelp(source);
        }
    }

    private boolean requirePermission(CommandSource source, String permission) {
        if (source.hasPermission(Permissions.ADMIN) || source.hasPermission(permission)) {
            return true;
        }
        source.sendMessage(context.messages().get("no-permission"));
        return false;
    }

    private void sendHelp(CommandSource source) {
        source.sendMessage(context.messages().get("commands.help-header"));
    }

    // ---- check / checkip / inspect / debug ----

    private void handleCheck(CommandSource source, String[] args) {
        if (!requirePermission(source, Permissions.CHECK)) return;
        if (args.length < 2) {
            source.sendMessage(context.messages().get("commands.usage-check"));
            return;
        }
        withOnlinePlayer(source, args[1], (uuid, name, ip) -> runManualAnalysis(source, uuid, name, ip, false));
    }

    private void handleCheckIp(CommandSource source, String[] args) {
        if (!requirePermission(source, Permissions.CHECK)) return;
        if (args.length < 2) {
            source.sendMessage(context.messages().get("commands.usage-checkip"));
            return;
        }
        String ip = args[1];
        if (!IpAddressUtils.isLiteralIpAddress(ip)) {
            source.sendMessage(context.messages().get("invalid-ip"));
            return;
        }
        runManualAnalysis(source, null, "console-check", ip, false);
    }

    private void handleInspect(CommandSource source, String[] args) {
        if (!requirePermission(source, Permissions.INSPECT)) return;
        if (args.length < 2) {
            source.sendMessage(context.messages().get("commands.usage-inspect"));
            return;
        }
        withOnlinePlayer(source, args[1], (uuid, name, ip) -> runManualAnalysis(source, uuid, name, ip, true));
    }

    private void handleDebug(CommandSource source, String[] args) {
        if (!requirePermission(source, Permissions.DEBUG)) return;
        if (args.length < 2) {
            source.sendMessage(context.messages().get("commands.usage-debug"));
            return;
        }
        withOnlinePlayer(source, args[1], (uuid, name, ip) -> runManualAnalysis(source, uuid, name, ip, true));
    }

    private interface OnlinePlayerAction {
        void run(UUID uuid, String name, String ip);
    }

    private void withOnlinePlayer(CommandSource source, String nameOrUuid, OnlinePlayerAction action) {
        playerLookup.findOnlinePlayer(nameOrUuid)
                .ifPresentOrElse(
                        info -> action.run(info.uuid(), info.name(), info.ip()),
                        () -> source.sendMessage(context.messages().get("player-not-found")));
    }

    private void runManualAnalysis(CommandSource source, UUID uuid, String name, String ip, boolean verbose) {
        ConnectionContext ctx = new ConnectionContext(null, name, ip, Platform.UNKNOWN, null, null,
                ConnectionContext.Stage.MANUAL, Instant.now());
        context.engine().analyzeConnection(ctx).thenAccept(decision -> sendReport(source, name, ip, decision, verbose));
    }

    private void sendReport(CommandSource source, String name, String ip, DecisionResult decision, boolean verbose) {
        source.sendMessage(context.messages().get("commands.report-header", Map.of("player", name, "ip", ip)));
        AggregatedDetectionResult aggregated = decision.aggregatedResult();
        if (aggregated == null) {
            source.sendMessage(context.messages().get("commands.report-no-detection"));
        } else {
            source.sendMessage(context.messages().get("commands.report-country", Map.of(
                    "country", nullToUnknown(aggregated.country()), "continent", nullToUnknown(aggregated.continent()))));
            source.sendMessage(context.messages().get("commands.report-network", Map.of(
                    "asn", aggregated.asn() == null ? "?" : String.valueOf(aggregated.asn()),
                    "isp", nullToUnknown(aggregated.isp()), "org", nullToUnknown(aggregated.organization()))));
            source.sendMessage(context.messages().get("commands.report-flags", Map.of(
                    "vpn", String.valueOf(aggregated.vpn()), "proxy", String.valueOf(aggregated.proxy()),
                    "hosting", String.valueOf(aggregated.hosting()), "datacenter", String.valueOf(aggregated.datacenter()),
                    "tor", String.valueOf(aggregated.tor()), "residential_proxy", String.valueOf(aggregated.residentialProxy()))));
            if (verbose) {
                for (DetectionResult r : aggregated.providerResults()) {
                    source.sendMessage(context.messages().get("commands.report-provider", Map.of(
                            "provider", r.provider(),
                            "status", r.isFailed() ? "ERROR: " + r.error() : "ok",
                            "cached", String.valueOf(r.cached()),
                            "latency", r.latency().toMillis() + "ms")));
                }
            }
        }
        source.sendMessage(context.messages().get("commands.report-risk", Map.of(
                "score", String.valueOf(decision.riskScore()), "level", decision.riskLevel().name())));
        source.sendMessage(context.messages().get("commands.report-decision", Map.of(
                "decision", decision.decision().name(), "reason", decision.reason().name())));
    }

    private String nullToUnknown(String value) {
        return value == null ? "?" : value;
    }

    // ---- reload / status / providers / cache ----

    private void handleReload(CommandSource source) {
        if (!requirePermission(source, Permissions.RELOAD)) return;
        context.reloadAll();
        source.sendMessage(context.messages().get("reload"));
    }

    private void handleStatus(CommandSource source) {
        if (!requirePermission(source, Permissions.STATUS)) return;
        Map<MetricKey, Long> snapshot = context.metrics().snapshot();
        source.sendMessage(context.messages().get("commands.status-header"));
        for (MetricKey key : MetricKey.values()) {
            source.sendMessage("&7" + key.name() + "&8: &f" + snapshot.getOrDefault(key, 0L));
        }
        source.sendMessage("&7AVG_PROVIDER_LATENCY_MS&8: &f"
                + String.format(Locale.ROOT, "%.1f", context.metrics().averageProviderLatencyMillis()));
    }

    private void handleProviders(CommandSource source) {
        if (!requirePermission(source, Permissions.PROVIDERS)) return;
        source.sendMessage(context.messages().get("commands.providers-header"));
        for (DetectionProvider provider : context.providers()) {
            ProviderHealthSnapshot health = provider.health().snapshot();
            source.sendMessage(context.messages().get("commands.providers-entry", Map.of(
                    "name", provider.name(),
                    "enabled", String.valueOf(provider.isEnabled()),
                    "status", health.status().name(),
                    "latency", health.averageLatency().toMillis() + "ms",
                    "errors", String.valueOf(health.errorCount()))));
        }
    }

    private void handleCache(CommandSource source, String[] args) {
        if (!requirePermission(source, Permissions.CACHE)) return;
        if (args.length >= 2 && args[1].equalsIgnoreCase("clear")) {
            context.detectionCache().invalidateAll();
            source.sendMessage(context.messages().get("commands.cache-cleared"));
            return;
        }
        CacheStats stats = context.detectionCache().stats();
        source.sendMessage(context.messages().get("commands.cache-status", Map.of(
                "size", String.valueOf(stats.size()), "max", String.valueOf(stats.maxSize()),
                "hits", String.valueOf(stats.hits()), "misses", String.valueOf(stats.misses()),
                "hitrate", String.format(Locale.ROOT, "%.1f", stats.hitRate() * 100))));
    }

    // ---- whitelist ----

    private void handleWhitelist(CommandSource source, String[] args) {
        if (!requirePermission(source, Permissions.WHITELIST)) return;
        if (args.length < 2) {
            source.sendMessage(context.messages().get("commands.usage-whitelist"));
            return;
        }
        switch (args[1].toLowerCase(Locale.ROOT)) {
            case "add" -> {
                if (args.length < 3) {
                    source.sendMessage(context.messages().get("commands.usage-whitelist"));
                    return;
                }
                Duration duration = parseOptionalDuration(source, args, 3);
                if (duration == PARSE_FAILED) return;
                addToWhitelist(source, args[2], duration);
            }
            case "remove" -> {
                if (args.length < 3) {
                    source.sendMessage(context.messages().get("commands.usage-whitelist"));
                    return;
                }
                removeFromWhitelist(source, args[2]);
            }
            case "list" -> listWhitelist(source);
            default -> source.sendMessage(context.messages().get("commands.usage-whitelist"));
        }
    }

    private void addToWhitelist(CommandSource source, String target, Duration duration) {
        String reason = "added via /antivpn whitelist add";
        if (IpAddressUtils.isLiteralIpAddress(target)) {
            context.whitelistService().addIp(target, duration, reason, source.name());
        } else if (target.contains("/")) {
            context.whitelistService().addCidr(target, duration, reason, source.name());
        } else {
            UUID uuid = resolvePlayerArgument(source, target);
            if (uuid == null) return;
            context.whitelistService().addPlayer(uuid, duration, reason, source.name());
        }
        source.sendMessage(context.messages().get("commands.whitelist-added", Map.of("target", target)));
    }

    private void removeFromWhitelist(CommandSource source, String target) {
        boolean removed;
        if (IpAddressUtils.isLiteralIpAddress(target)) {
            removed = context.whitelistService().remove(WhitelistEntryType.IP, IpAddressUtils.normalize(target));
        } else if (target.contains("/")) {
            removed = context.whitelistService().remove(WhitelistEntryType.CIDR, target);
        } else {
            UUID uuid = resolvePlayerArgument(source, target);
            if (uuid == null) return;
            removed = context.whitelistService().remove(WhitelistEntryType.PLAYER, uuid.toString());
        }
        source.sendMessage(context.messages().get(removed ? "commands.whitelist-removed" : "commands.entry-not-found",
                Map.of("target", target)));
    }

    private void listWhitelist(CommandSource source) {
        source.sendMessage(context.messages().get("commands.whitelist-list-header"));
        for (WhitelistEntry entry : context.whitelistService().list()) {
            source.sendMessage(formatEntryLine(entry.type().name(), entry.value(), entry.reason(),
                    entry.addedBy(), entry.expiresAt()));
        }
    }

    // ---- blacklist ----

    private void handleBlacklist(CommandSource source, String[] args) {
        if (!requirePermission(source, Permissions.BLACKLIST)) return;
        if (args.length < 2) {
            source.sendMessage(context.messages().get("commands.usage-blacklist"));
            return;
        }
        switch (args[1].toLowerCase(Locale.ROOT)) {
            case "add" -> {
                if (args.length < 3) {
                    source.sendMessage(context.messages().get("commands.usage-blacklist"));
                    return;
                }
                Duration duration = parseOptionalDuration(source, args, 3);
                if (duration == PARSE_FAILED) return;
                addToBlacklist(source, args[2], duration);
            }
            case "remove" -> {
                if (args.length < 3) {
                    source.sendMessage(context.messages().get("commands.usage-blacklist"));
                    return;
                }
                removeFromBlacklist(source, args[2]);
            }
            case "list" -> listBlacklist(source);
            default -> source.sendMessage(context.messages().get("commands.usage-blacklist"));
        }
    }

    private void addToBlacklist(CommandSource source, String target, Duration duration) {
        String reason = "added via /antivpn blacklist add";
        if (IpAddressUtils.isLiteralIpAddress(target)) {
            context.blacklistService().addIp(target, duration, reason, source.name());
        } else if (target.contains("/")) {
            context.blacklistService().addCidr(target, duration, reason, source.name());
        } else if (target.chars().allMatch(Character::isDigit)) {
            context.blacklistService().addAsn(Integer.parseInt(target), duration, reason, source.name());
        } else {
            UUID uuid = resolvePlayerArgument(source, target);
            if (uuid == null) return;
            context.blacklistService().addPlayer(uuid, duration, reason, source.name());
        }
        source.sendMessage(context.messages().get("commands.blacklist-added", Map.of("target", target)));
    }

    private void removeFromBlacklist(CommandSource source, String target) {
        boolean removed;
        if (IpAddressUtils.isLiteralIpAddress(target)) {
            removed = context.blacklistService().remove(BlacklistEntryType.IP, IpAddressUtils.normalize(target));
        } else if (target.contains("/")) {
            removed = context.blacklistService().remove(BlacklistEntryType.CIDR, target);
        } else if (target.chars().allMatch(Character::isDigit)) {
            removed = context.blacklistService().remove(BlacklistEntryType.ASN, target);
        } else {
            UUID uuid = resolvePlayerArgument(source, target);
            if (uuid == null) return;
            removed = context.blacklistService().remove(BlacklistEntryType.PLAYER, uuid.toString());
        }
        source.sendMessage(context.messages().get(removed ? "commands.blacklist-removed" : "commands.entry-not-found",
                Map.of("target", target)));
    }

    private void listBlacklist(CommandSource source) {
        source.sendMessage(context.messages().get("commands.blacklist-list-header"));
        for (BlacklistEntry entry : context.blacklistService().list()) {
            source.sendMessage(formatEntryLine(entry.type().name(), entry.value(), entry.reason(),
                    entry.addedBy(), entry.expiresAt()));
        }
    }

    // ---- bypass ----

    private void handleBypass(CommandSource source, String[] args) {
        if (!requirePermission(source, Permissions.BYPASS)) return;
        if (args.length < 2) {
            source.sendMessage(context.messages().get("commands.usage-bypass"));
            return;
        }
        if (args[1].equalsIgnoreCase("list")) {
            source.sendMessage(context.messages().get("commands.bypass-list-header"));
            for (BypassEntry entry : context.bypassService().listActive()) {
                source.sendMessage(formatEntryLine("PLAYER", entry.uuid().toString(), entry.reason(),
                        entry.createdBy(), entry.expiresAt()));
            }
            return;
        }
        if (args[1].equalsIgnoreCase("remove")) {
            if (args.length < 3) {
                source.sendMessage(context.messages().get("commands.usage-bypass"));
                return;
            }
            UUID uuid = resolvePlayerArgument(source, args[2]);
            if (uuid == null) return;
            context.bypassService().revoke(uuid);
            source.sendMessage(context.messages().get("commands.bypass-removed", Map.of("target", args[2])));
            return;
        }
        // /antivpn bypass <player> <duration>
        if (args.length < 3) {
            source.sendMessage(context.messages().get("commands.usage-bypass"));
            return;
        }
        UUID uuid = resolvePlayerArgument(source, args[1]);
        if (uuid == null) return;
        Duration duration = parseDuration(source, args[2]);
        if (duration == null) return;
        String ip = playerLookup.findOnlinePlayer(args[1]).map(PlayerLookup.PlayerInfo::ip).orElse("unknown");
        context.bypassService().grant(uuid, ip, duration, "granted via /antivpn bypass", source.name());
        source.sendMessage(context.messages().get("commands.bypass-active", Map.of(
                "target", args[1], "duration", DurationUtils.format(duration))));
    }

    // ---- trust / untrust (false-positive correction) ----

    private void handleTrust(CommandSource source, String[] args, boolean trust) {
        if (!requirePermission(source, Permissions.TRUST)) return;
        if (args.length < 2) {
            source.sendMessage(context.messages().get(trust ? "commands.usage-trust" : "commands.usage-untrust"));
            return;
        }
        UUID uuid = resolvePlayerArgument(source, args[1]);
        if (uuid == null) return;
        if (trust) {
            context.whitelistService().addPlayer(uuid, null, "false-positive correction via /antivpn trust", source.name());
            source.sendMessage(context.messages().get("commands.trust-added", Map.of("target", args[1])));
        } else {
            context.whitelistService().remove(WhitelistEntryType.PLAYER, uuid.toString());
            source.sendMessage(context.messages().get("commands.untrust-removed", Map.of("target", args[1])));
        }
    }

    // ---- helpers ----

    private static final Duration PARSE_FAILED = Duration.ZERO.minusSeconds(1);

    private Duration parseOptionalDuration(CommandSource source, String[] args, int index) {
        if (args.length <= index) {
            return null;
        }
        return parseDuration(source, args[index]);
    }

    private Duration parseDuration(CommandSource source, String text) {
        try {
            return DurationUtils.parse(text);
        } catch (IllegalArgumentException e) {
            source.sendMessage(context.messages().get("invalid-duration"));
            return PARSE_FAILED;
        }
    }

    private UUID resolvePlayerArgument(CommandSource source, String nameOrUuid) {
        UUID direct = UuidUtils.parseOrNull(nameOrUuid);
        if (direct != null) {
            return direct;
        }
        var online = playerLookup.findOnlinePlayer(nameOrUuid);
        if (online.isPresent()) {
            return online.get().uuid();
        }
        source.sendMessage(context.messages().get("player-not-found"));
        return null;
    }

    private String formatEntryLine(String type, String value, String reason, String addedBy, Instant expiresAt) {
        String expiry = expiresAt == null ? "permanent" : TIME_FORMAT.format(
                java.time.ZonedDateTime.ofInstant(expiresAt, java.time.ZoneOffset.UTC));
        return "&7[" + type + "] &f" + value + " &8- &7" + reason + " &8(&7" + addedBy + "&8, expires: " + expiry + "&8)";
    }
}
