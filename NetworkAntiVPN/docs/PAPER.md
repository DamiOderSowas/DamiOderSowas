# Paper Integration

`antivpn-paper` is fully capable of running the whole detection pipeline
**standalone** - no proxy required. It only reuses a proxy-forwarded result
opportunistically.

## Connection flow

`integration.paper.PaperConnectionListener` hooks
`AsyncPlayerPreLoginEvent`, which Bukkit explicitly documents as firing off
the main server thread - this makes it safe to synchronously wait
(`.join()`) on the (already asynchronous) NetworkAntiVPN pipeline inside
the handler without stalling the server or the calling thread pool, exactly
matching Bukkit's own intended usage of this event.

Order of operations:

1. If `paper.reuse-proxy-result` is enabled, check
   `AntiVpnService.getSessionResult(uuid)` - populated by
   `PaperResultListener` when Velocity/BungeeCord forwarded a result (see
   [VELOCITY.md](VELOCITY.md) for the exact timing and its limitations).
2. If nothing was forwarded (or the setting is disabled, or this server has
   no proxy in front of it at all), run the full pipeline itself.
3. Apply the decision: block/kick with the `messages.yml` message for
   `BLOCK`/`TEMPORARY_BLOCK`/`RATE_LIMITED`/`REQUIRE_VERIFICATION`; queue an
   in-game warning message for `ALLOW_WITH_WARNING`, delivered on
   `PlayerJoinEvent` (a `Player`/chat is not available yet during
   `AsyncPlayerPreLoginEvent`).

`PlayerQuitEvent` clears the stored session result and any pending warning.

## Receiving the proxy's result

`integration.paper.PaperResultListener` (a Bukkit
`PluginMessageListener` on channel `networkantivpn:sync`) decodes the
payload via `core.SessionResultPayload` and stores it into this JVM's own
`AntiVpnEngine` session map. See [VELOCITY.md](VELOCITY.md#forwarding-the-result-to-the-backend)
for exactly when this can and cannot arrive before this server's own
`AsyncPlayerPreLoginEvent` check.

## Why detection also runs fine with no proxy at all

Every service `antivpn-paper` needs (`AntiVpnEngine`, cache, whitelist,
blacklist, bypass, country filter, providers) is constructed locally by
`core.AntiVpnBootstrap.bootstrap(getDataFolder().toPath())` in
`PaperAntiVpnPlugin#onEnable` - there is no compile-time or runtime
dependency on Velocity/BungeeCord being present.

## Logging

Modern Paper already ships a working SLF4J binding (backed by its own
Log4j2 core) on the server classpath. `antivpn-core`'s plain
`LoggerFactory.getLogger(...)` calls are routed to the console
automatically - nothing is bridged or shaded in `antivpn-paper`.

## Commands

`integration.paper.PaperAntiVpnCommand` implements Bukkit's
`CommandExecutor`/`TabCompleter`, delegating to the shared
`commands.AntiVpnCommandExecutor`. Registered under the `antivpn` command
in `plugin.yml`. See [COMMANDS.md](COMMANDS.md).
