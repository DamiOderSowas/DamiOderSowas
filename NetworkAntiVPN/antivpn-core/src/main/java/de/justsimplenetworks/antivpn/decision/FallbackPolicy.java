package de.justsimplenetworks.antivpn.decision;

/**
 * What to do when detection could not run at all - every configured
 * provider failed, timed out, or none are enabled. Configured via
 * {@code fallback.policy} in {@code config.yml}.
 * <p>
 * Default: {@link #FAIL_OPEN}. A total provider outage should not lock an
 * entire live Minecraft network out of the server; the event is still
 * logged at an elevated level and counted in metrics so staff notice and
 * can investigate. Networks with stricter security requirements should set
 * this to {@link #REQUIRE_VERIFICATION} or {@link #FAIL_CLOSED} - see
 * {@code docs/PROVIDERS.md#fallback}.
 */
public enum FallbackPolicy {
    /** Allow the connection (logged) as if no threat was detected. */
    FAIL_OPEN,
    /** Block the connection until at least one provider is reachable again. */
    FAIL_CLOSED,
    /** Require additional verification instead of an outright allow or block. */
    REQUIRE_VERIFICATION
}
