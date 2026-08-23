package de.justsimplenetworks.antivpn.api;

import de.justsimplenetworks.antivpn.api.event.EventBus;

import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * The public entry point into NetworkAntiVPN, implemented by
 * {@code core.AntiVpnEngine} and injected into every platform integration.
 * <p>
 * This is also the seam a future {@code NetworkCoreBridge} or any other
 * JustSimpleNetworks plugin should depend on instead of reaching into
 * internal packages - it is the only class in this project that is
 * guaranteed to stay source-compatible across releases.
 */
public interface AntiVpnService {

    /**
     * Runs the full pipeline (cache -&gt; providers -&gt; aggregation -&gt; scoring
     * -&gt; whitelist/blacklist/bypass -&gt; country filter -&gt; rate limit -&gt; decision)
     * for a connection. Never blocks the calling thread: all provider I/O
     * happens asynchronously and the returned future completes on a
     * background executor.
     */
    CompletableFuture<DecisionResult> analyzeConnection(ConnectionContext context);

    /**
     * Looks up a decision already computed for this player during this
     * session (typically produced on the proxy and forwarded to the
     * backend). Backend integrations must call this before running their
     * own analysis to avoid a redundant provider check.
     */
    Optional<DecisionResult> getSessionResult(UUID playerUuid);

    /** Stores a decision for the duration of the player's session (see {@link #getSessionResult(UUID)}). */
    void storeSessionResult(UUID playerUuid, DecisionResult result);

    /** Removes a stored session result, e.g. on disconnect. */
    void clearSessionResult(UUID playerUuid);

    /** Returns the privacy-conscious risk profile for a player's most recent connection, if any. */
    Optional<PlayerRiskProfile> getRiskProfile(UUID playerUuid);

    /** The event bus other systems can subscribe to. */
    EventBus events();

    /** Reloads configuration, whitelist/blacklist/bypass stores and provider settings from disk. */
    void reload();

    /** Releases background resources (executors, cache cleanup tasks, HTTP clients). Call on plugin disable. */
    void shutdown();
}
