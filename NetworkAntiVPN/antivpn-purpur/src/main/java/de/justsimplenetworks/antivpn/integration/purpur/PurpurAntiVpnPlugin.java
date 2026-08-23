package de.justsimplenetworks.antivpn.integration.purpur;

import de.justsimplenetworks.antivpn.integration.paper.PaperAntiVpnPlugin;

/**
 * Purpur backend integration. Purpur is API-compatible with Paper (it is
 * built directly on top of Paper), so this class intentionally adds no
 * logic of its own - it exists to give the plugin distinct, Purpur-branded
 * metadata (see {@code plugin.yml}) and a home for genuinely Purpur-only
 * enhancements in the future (e.g. reading Purpur-specific server flags).
 * All detection/decision behaviour comes from
 * {@link PaperAntiVpnPlugin#onEnable()}.
 */
public final class PurpurAntiVpnPlugin extends PaperAntiVpnPlugin {
}
