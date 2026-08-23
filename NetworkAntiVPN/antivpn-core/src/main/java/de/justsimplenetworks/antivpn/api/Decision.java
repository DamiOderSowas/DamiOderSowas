package de.justsimplenetworks.antivpn.api;

/**
 * The outcome of the {@code DecisionEngine} for a single connection attempt.
 * Decisions are produced by evaluating configurable rules (see
 * {@code decision.DecisionRuleSet}); no decision is ever hard-coded in Java.
 */
public enum Decision {
    /** Connection is allowed with no extra logging beyond the standard audit trail. */
    ALLOW,
    /** Connection is allowed, but the event is logged at an elevated level for staff review. */
    ALLOW_WITH_LOG,
    /** Connection is allowed, and the player receives a warning message. */
    ALLOW_WITH_WARNING,
    /** Connection requires additional verification (e.g. captcha/2FA) before being allowed. */
    REQUIRE_VERIFICATION,
    /** Connection is blocked for a limited, automatically-expiring duration. */
    TEMPORARY_BLOCK,
    /** Connection is blocked outright. */
    BLOCK,
    /** Connection bypasses all further checks (whitelist / trusted / temporary bypass). */
    BYPASS,
    /** Connection was rejected because a rate limit was exceeded. */
    RATE_LIMITED
}
