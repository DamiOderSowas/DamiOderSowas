package de.justsimplenetworks.antivpn.api;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Objects;

/**
 * The result a single {@code DetectionProvider} produced for one IP address.
 * This is intentionally a plain data carrier: a provider must never decide
 * whether a connection is allowed, it only reports what it observed together
 * with a confidence value. All boolean flags are nullable-by-convention via
 * {@link Boolean} wrappers so a provider can honestly report "unknown"
 * instead of guessing false.
 *
 * @param ip               the IPv4/IPv6 address that was checked
 * @param timestamp         when the result was produced
 * @param provider           the name of the provider that produced this result
 * @param vpn                whether the IP was identified as a VPN endpoint
 * @param proxy              whether the IP was identified as a generic proxy
 * @param hosting            whether the IP belongs to a hosting/cloud network
 * @param datacenter         whether the IP belongs to a datacenter ASN
 * @param tor                whether the IP is a known Tor exit node
 * @param residentialProxy   whether the IP looks like a residential proxy (a
 *                           proxy tunnelled through a residential ISP connection)
 * @param residential        whether the IP is a genuine residential ISP address
 * @param country            ISO 3166-1 alpha-2 country code, or {@code null} if unknown
 * @param continent          continent code (AF, AN, AS, EU, NA, OC, SA), or {@code null}
 * @param asn                autonomous system number, or {@code null} if unknown
 * @param isp                ISP name, or {@code null} if unknown
 * @param organization       organization name, or {@code null} if unknown
 * @param hostname           reverse DNS hostname, informational only - never used
 *                           as the basis for country detection
 * @param confidence         provider confidence in this result, {@code 0.0-1.0}
 * @param riskScore          provider-reported risk indicator {@code 0-100}, or
 *                           {@code null} if the provider does not report one;
 *                           this is one input into the network-wide
 *                           {@code RiskScoringEngine}, not the final score
 * @param evidence           short human-readable reasons backing this result,
 *                           used for {@code /antivpn debug}
 * @param latency            how long the provider request took
 * @param cached             whether this result was served from the detection cache
 * @param error              non-null if the provider failed; when set, all
 *                           boolean/score fields should be treated as unknown
 */
public record DetectionResult(
        String ip,
        Instant timestamp,
        String provider,
        Boolean vpn,
        Boolean proxy,
        Boolean hosting,
        Boolean datacenter,
        Boolean tor,
        Boolean residentialProxy,
        Boolean residential,
        String country,
        String continent,
        Integer asn,
        String isp,
        String organization,
        String hostname,
        double confidence,
        Integer riskScore,
        List<String> evidence,
        Duration latency,
        boolean cached,
        String error
) {

    public DetectionResult {
        Objects.requireNonNull(ip, "ip must not be null");
        Objects.requireNonNull(timestamp, "timestamp must not be null");
        Objects.requireNonNull(provider, "provider must not be null");
        evidence = evidence == null ? List.of() : List.copyOf(evidence);
        confidence = Math.max(0.0, Math.min(1.0, confidence));
    }

    public boolean isFailed() {
        return error != null;
    }

    /** Returns a copy of this result with only {@link #cached()} changed. */
    public DetectionResult withCached(boolean newCached) {
        return new DetectionResult(ip, timestamp, provider, vpn, proxy, hosting, datacenter, tor,
                residentialProxy, residential, country, continent, asn, isp, organization, hostname,
                confidence, riskScore, evidence, latency, newCached, error);
    }

    public boolean isSuspicious() {
        return Boolean.TRUE.equals(vpn)
                || Boolean.TRUE.equals(proxy)
                || Boolean.TRUE.equals(hosting)
                || Boolean.TRUE.equals(datacenter)
                || Boolean.TRUE.equals(tor)
                || Boolean.TRUE.equals(residentialProxy);
    }

    public static Builder builder(String ip, String provider) {
        return new Builder(ip, provider);
    }

    public static DetectionResult error(String ip, String provider, String errorMessage) {
        return new Builder(ip, provider)
                .error(errorMessage)
                .confidence(0.0)
                .build();
    }

    /** Fluent builder; keeps {@link DetectionResult} itself a plain immutable record. */
    public static final class Builder {
        private final String ip;
        private final String provider;
        private Instant timestamp = Instant.now();
        private Boolean vpn;
        private Boolean proxy;
        private Boolean hosting;
        private Boolean datacenter;
        private Boolean tor;
        private Boolean residentialProxy;
        private Boolean residential;
        private String country;
        private String continent;
        private Integer asn;
        private String isp;
        private String organization;
        private String hostname;
        private double confidence = 1.0;
        private Integer riskScore;
        private List<String> evidence = List.of();
        private Duration latency = Duration.ZERO;
        private boolean cached;
        private String error;

        private Builder(String ip, String provider) {
            this.ip = ip;
            this.provider = provider;
        }

        public Builder timestamp(Instant timestamp) { this.timestamp = timestamp; return this; }
        public Builder vpn(Boolean vpn) { this.vpn = vpn; return this; }
        public Builder proxy(Boolean proxy) { this.proxy = proxy; return this; }
        public Builder hosting(Boolean hosting) { this.hosting = hosting; return this; }
        public Builder datacenter(Boolean datacenter) { this.datacenter = datacenter; return this; }
        public Builder tor(Boolean tor) { this.tor = tor; return this; }
        public Builder residentialProxy(Boolean residentialProxy) { this.residentialProxy = residentialProxy; return this; }
        public Builder residential(Boolean residential) { this.residential = residential; return this; }
        public Builder country(String country) { this.country = country; return this; }
        public Builder continent(String continent) { this.continent = continent; return this; }
        public Builder asn(Integer asn) { this.asn = asn; return this; }
        public Builder isp(String isp) { this.isp = isp; return this; }
        public Builder organization(String organization) { this.organization = organization; return this; }
        public Builder hostname(String hostname) { this.hostname = hostname; return this; }
        public Builder confidence(double confidence) { this.confidence = confidence; return this; }
        public Builder riskScore(Integer riskScore) { this.riskScore = riskScore; return this; }
        public Builder evidence(List<String> evidence) { this.evidence = evidence; return this; }
        public Builder latency(Duration latency) { this.latency = latency; return this; }
        public Builder cached(boolean cached) { this.cached = cached; return this; }
        public Builder error(String error) { this.error = error; return this; }

        public DetectionResult build() {
            return new DetectionResult(ip, timestamp, provider, vpn, proxy, hosting, datacenter, tor,
                    residentialProxy, residential, country, continent, asn, isp, organization, hostname,
                    confidence, riskScore, evidence, latency, cached, error);
        }
    }
}
