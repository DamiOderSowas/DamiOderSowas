package de.justsimplenetworks.antivpn.api;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Combines every {@link DetectionResult} collected for one IP (one per
 * queried provider) into a single, provider-agnostic verdict. Produced by
 * {@code detection.DetectionAggregator} and consumed by
 * {@code scoring.RiskScoringEngine} and {@code decision.DecisionEngine}.
 * <p>
 * Boolean flags use majority-vote-with-confidence-weighting semantics: a
 * flag is {@code true} only if the weighted evidence across all successful
 * providers crosses the configured agreement threshold. See
 * {@code detection.DetectionAggregator} for the exact algorithm.
 *
 * @param ip                 the checked IP address
 * @param timestamp          when the aggregation was produced
 * @param providerResults    the raw per-provider results this verdict is based on
 * @param vpn                aggregated VPN verdict
 * @param proxy              aggregated proxy verdict
 * @param hosting            aggregated hosting verdict
 * @param datacenter         aggregated datacenter verdict
 * @param tor                aggregated Tor verdict
 * @param residentialProxy   aggregated residential-proxy verdict
 * @param residential        aggregated residential verdict
 * @param country            resolved ISO 3166-1 alpha-2 country code, or {@code null}
 * @param continent          resolved continent code, or {@code null}
 * @param asn                resolved ASN, or {@code null}
 * @param isp                resolved ISP name, or {@code null}
 * @param organization       resolved organization name, or {@code null}
 * @param confidence         overall confidence in the aggregated verdict, {@code 0.0-1.0}
 * @param providerAgreement  fraction of successful providers that agreed with the majority, {@code 0.0-1.0}
 * @param evidence           merged human-readable evidence from all providers
 * @param fromCache          whether this aggregated result was served entirely from cache
 */
public record AggregatedDetectionResult(
        String ip,
        Instant timestamp,
        List<DetectionResult> providerResults,
        boolean vpn,
        boolean proxy,
        boolean hosting,
        boolean datacenter,
        boolean tor,
        boolean residentialProxy,
        boolean residential,
        String country,
        String continent,
        Integer asn,
        String isp,
        String organization,
        double confidence,
        double providerAgreement,
        List<String> evidence,
        boolean fromCache
) {

    public AggregatedDetectionResult {
        Objects.requireNonNull(ip, "ip must not be null");
        Objects.requireNonNull(timestamp, "timestamp must not be null");
        providerResults = providerResults == null ? List.of() : List.copyOf(providerResults);
        evidence = evidence == null ? List.of() : List.copyOf(evidence);
    }

    public boolean isSuspicious() {
        return vpn || proxy || hosting || datacenter || tor || residentialProxy;
    }

    public long successfulProviderCount() {
        return providerResults.stream().filter(r -> !r.isFailed()).count();
    }

    public long failedProviderCount() {
        return providerResults.stream().filter(DetectionResult::isFailed).count();
    }

    public Optional<DetectionResult> resultOf(String providerName) {
        return providerResults.stream().filter(r -> r.provider().equals(providerName)).findFirst();
    }

    /** Aggregated result representing "no provider could be queried" (all failed or none configured). */
    public static AggregatedDetectionResult unknown(String ip, List<DetectionResult> providerResults) {
        return new AggregatedDetectionResult(ip, Instant.now(), providerResults,
                false, false, false, false, false, false, false,
                null, null, null, null, null,
                0.0, 0.0, List.of("no provider data available"), false);
    }
}
