package de.justsimplenetworks.antivpn.core;

import de.justsimplenetworks.antivpn.blacklist.BlacklistService;
import de.justsimplenetworks.antivpn.bypass.BypassService;
import de.justsimplenetworks.antivpn.cache.DetectionCache;
import de.justsimplenetworks.antivpn.config.AntiVpnConfig;
import de.justsimplenetworks.antivpn.config.ConfigLoader;
import de.justsimplenetworks.antivpn.config.MessagesConfig;
import de.justsimplenetworks.antivpn.metrics.MetricsRegistry;
import de.justsimplenetworks.antivpn.providers.DetectionProvider;
import de.justsimplenetworks.antivpn.whitelist.WhitelistService;

import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.ExecutorService;

/**
 * Everything a platform integration or the {@code commands} package needs,
 * bundled together by {@link AntiVpnBootstrap}. {@link #engine()} is the
 * only thing other JustSimpleNetworks plugins should hold onto long-term
 * (via the {@code api.AntiVpnService} interface); the rest exists for the
 * admin command implementations.
 */
public record AntiVpnContext(
        AntiVpnEngine engine,
        AntiVpnConfig config,
        MessagesConfig messages,
        WhitelistService whitelistService,
        BlacklistService blacklistService,
        BypassService bypassService,
        DetectionCache detectionCache,
        MetricsRegistry metrics,
        List<DetectionProvider> providers,
        ExecutorService providerExecutor,
        Path dataDirectory
) {

    /**
     * Re-reads {@code config.yml} and {@code messages.yml} from disk and
     * applies everything that can be hot-reloaded (whitelist, blacklist,
     * country rules, risk weights, decision rules, messages). The configured
     * provider list and executor sizing require a full restart to change -
     * see {@code docs/TROUBLESHOOTING.md}.
     */
    public void reloadAll() {
        config.reload(ConfigLoader.loadOrCreate(dataDirectory, "config.yml"));
        messages.reload(ConfigLoader.loadOrCreate(dataDirectory, "messages.yml"));
        engine.reload();
    }

    public void shutdown() {
        engine.shutdown();
        providerExecutor.shutdown();
    }
}
