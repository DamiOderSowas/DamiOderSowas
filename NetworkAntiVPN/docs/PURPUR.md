# Purpur Integration

Purpur's plugin API is a strict superset of Paper's - Purpur is built
directly on top of Paper. `antivpn-purpur` therefore adds **no** detection
logic of its own: `integration.purpur.PurpurAntiVpnPlugin` is a one-line
subclass of `integration.paper.PaperAntiVpnPlugin`, existing only to:

- give the plugin distinct, Purpur-branded metadata (`plugin.yml`: name
  `NetworkAntiVPN-Purpur`, main class in the `integration.purpur` package)
  so `/plugins` and log output clearly identify it as the Purpur build;
- provide a home for genuinely Purpur-only enhancements later (e.g. reading
  Purpur-specific server flags/config), without touching
  `antivpn-paper`.

## Do I need this module?

No - installing `antivpn-paper`'s jar directly on a Purpur server works
identically, since Purpur is Paper-API-compatible. Use `antivpn-purpur`
only if you specifically want the Purpur-branded identity in `/plugins` and
logs, or if you plan to build genuinely Purpur-specific features on top of
it.

## Everything else

See [PAPER.md](PAPER.md) - connection flow, result reuse, logging, and
commands are all identical, since `PurpurAntiVpnPlugin` inherits
`PaperAntiVpnPlugin#onEnable()`/`#onDisable()` unmodified.
