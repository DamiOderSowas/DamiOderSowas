# Troubleshooting

## Build

### `mvn clean verify` fails resolving `com.velocitypowered:velocity-api`, `io.papermc.paper:paper-api`, or `net.md-5:bungeecord-api`

Those four platform modules (`antivpn-velocity`, `antivpn-paper`,
`antivpn-purpur`, `antivpn-bungeecord`) declare their platform API as a
`provided`-scope dependency resolved from `repo.papermc.io` (and, for
BungeeCord, additionally Sonatype's OSS snapshots repository) - neither is
mirrored on Maven Central. If your build environment blocks outbound
network access to those hosts, only `antivpn-core` will build; the platform
modules need that network access (which real CI, and any normal developer
machine, has - see `.github/workflows/build.yml`).

`antivpn-core` itself only needs Maven Central and builds/tests fully
offline once dependencies are cached.

### "release version 25 not supported" / "invalid target release: 25"

You need a JDK 25 to compile. Install one (e.g.
`sudo apt-get install openjdk-25-jdk-headless` on Debian/Ubuntu, or via
[Adoptium](https://adoptium.net)) and point `JAVA_HOME` at it, or configure
a Maven/Gradle toolchain. This project targets Java 25 intentionally per
its specification; there is no lower-Java compatibility mode.

### Gradle fails with "Unsupported class file major version 69" when just *invoking* `gradle`/`./gradlew`

This means the Gradle **daemon itself** was launched under JDK 25, which
this Gradle version does not support running on (as opposed to *compiling
for*, which is fully supported via the configured toolchain). Run Gradle
itself under JDK 21 (`JAVA_HOME=/path/to/jdk21 ./gradlew build`); the
`java.toolchain.languageVersion = 25` in the root `build.gradle` still
makes Gradle compile/test the actual project code against JDK 25.

### Gradle can't find/download a JDK 25 toolchain

Gradle's toolchain auto-provisioning needs network access to resolve a JDK
(via the Foojay Disco API) if one isn't already installed locally. Install
a JDK 25 yourself and Gradle will detect and use it instead.

## Runtime

### A block/verification message says "(missing message: X)"

The corresponding key is missing from `messages.yml` (it was deleted or the
file predates an update). Copy the key from the bundled default
`messages.yml` in `antivpn-core/src/main/resources` or delete your
`messages.yml` to regenerate it (this discards any customizations - back it
up first).

### `/antivpn reload` doesn't seem to change provider behaviour

By design - the configured provider list, HTTP clients, and the provider
thread pool are constructed once at startup
(`core.AntiVpnBootstrap.bootstrap`) and are **not** rebuilt on reload (only
whitelist/blacklist/country/risk-weights/decision-rules/messages are
hot-reloaded - see [CONFIGURATION.md](CONFIGURATION.md#reload-semantics)).
Restart the server/proxy after changing `providers.*`.

### Everyone is being allowed even though a provider looks misconfigured

Check `fallback.policy` (default `FAIL_OPEN`) - if every provider is
failing (bad URL, bad API key, network issue), NetworkAntiVPN allows
connections rather than locking out the network, and logs/counts this under
`PROVIDER_FALLBACK`. Run `/antivpn providers` to see per-provider health and
the last error.

### Paper/Purpur seems to run its own check even though Velocity already checked

This is expected in some cases - see
[VELOCITY.md#forwarding-the-result-to-the-backend](VELOCITY.md#forwarding-the-result-to-the-backend)
for exactly why Bukkit's plugin messaging cannot always beat the backend's
own `AsyncPlayerPreLoginEvent`, and what to expect instead.

### Real Minecraft version numbers

At the time of writing there is no released Minecraft version as high as
1.21.11-1.26.x; those figures in the project specification describe the
version *range* this codebase is written to remain compatible with as such
versions ship, using the highest currently-known Paper/Velocity API
snapshot version as the placeholder in `pom.xml`/`build.gradle`. Bump
`paper.api.version`/`velocity.api.version`/`bungeecord.api.version` (and
`api-version` in `plugin.yml`) once real corresponding builds exist.
