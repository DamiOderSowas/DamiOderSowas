package de.justsimplenetworks.antivpn.providers;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import de.justsimplenetworks.antivpn.api.DetectionResult;
import de.justsimplenetworks.antivpn.security.LogSanitizer;
import de.justsimplenetworks.antivpn.security.ResponseSizeLimiter;
import de.justsimplenetworks.antivpn.security.SsrfGuard;
import de.justsimplenetworks.antivpn.util.IpAddressUtils;
import de.justsimplenetworks.antivpn.util.JsonPathUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;

/**
 * A generic, JSON-over-HTTPS {@link DetectionProvider} whose endpoint,
 * authentication and response-field mapping are entirely driven by
 * {@link ProviderConfig} - no vendor-specific URL or response schema is
 * hard-coded anywhere in this class. Operators point {@code urlTemplate} at
 * whatever IP-intelligence API they have a contract with and describe how to
 * read its response via {@code fieldMapping}; see {@code docs/PROVIDERS.md}.
 * <p>
 * Hardening applied to every request: HTTPS-only + private-network SSRF
 * checks ({@link SsrfGuard}), no redirect following, a strict per-request
 * timeout, and a hard response-size cap ({@link ResponseSizeLimiter}).
 */
public final class ConfigurableHttpDetectionProvider implements DetectionProvider {

    private static final Logger LOGGER = LoggerFactory.getLogger(ConfigurableHttpDetectionProvider.class);
    private static final int MAX_RESPONSE_BYTES = 256 * 1024;

    private static final List<String> BOOLEAN_FIELDS = List.of(
            "vpn", "proxy", "hosting", "datacenter", "tor", "residentialProxy", "residential");
    private static final List<String> STRING_FIELDS = List.of(
            "country", "continent", "isp", "organization", "hostname");
    private static final List<String> INT_FIELDS = List.of("asn", "riskScore");

    private final ProviderConfig config;
    private final ProviderHealthTracker health;
    private final HttpClient httpClient;

    public ConfigurableHttpDetectionProvider(ProviderConfig config) {
        this(config, HttpClient.newBuilder()
                .connectTimeout(config.timeout())
                .followRedirects(HttpClient.Redirect.NEVER)
                .build());
    }

    /** Test-visible constructor allowing an {@link HttpClient} to be injected. */
    public ConfigurableHttpDetectionProvider(ProviderConfig config, HttpClient httpClient) {
        this.config = Objects.requireNonNull(config, "config must not be null");
        this.httpClient = Objects.requireNonNull(httpClient, "httpClient must not be null");
        this.health = new ProviderHealthTracker(config.name());
        if (config.urlTemplate() == null || config.urlTemplate().isBlank()) {
            throw new IllegalArgumentException(
                    "Provider '" + config.name() + "' is an HTTP provider but has no urlTemplate configured");
        }
        if (!config.urlTemplate().contains("{ip}")) {
            throw new IllegalArgumentException(
                    "Provider '" + config.name() + "' urlTemplate must contain the {ip} placeholder");
        }
        SsrfGuard.validateTemplate(config.urlTemplate());
    }

    @Override
    public String name() {
        return config.name();
    }

    @Override
    public ProviderConfig config() {
        return config;
    }

    @Override
    public ProviderHealthTracker health() {
        return health;
    }

    @Override
    public CompletableFuture<DetectionResult> detect(String ip) {
        if (!IpAddressUtils.isLiteralIpAddress(ip)) {
            return CompletableFuture.completedFuture(
                    DetectionResult.error(ip, config.name(), "refusing to query a non-literal IP address"));
        }

        Instant start = Instant.now();
        HttpRequest request;
        try {
            request = buildRequest(ip);
        } catch (RuntimeException e) {
            health.recordFailure(e.getMessage());
            LOGGER.warn("Provider {} rejected request for building safety reasons: {}",
                    LogSanitizer.sanitize(config.name()), LogSanitizer.sanitize(e.getMessage()));
            return CompletableFuture.completedFuture(DetectionResult.error(ip, config.name(), "request build failed"));
        }

        return httpClient.sendAsync(request, ResponseSizeLimiter.ofLimitedString(MAX_RESPONSE_BYTES))
                .handle((response, throwable) -> {
                    Duration latency = Duration.between(start, Instant.now());
                    if (throwable != null) {
                        health.recordFailure(throwable.getMessage());
                        LOGGER.debug("Provider {} request failed after {}ms: {}",
                                LogSanitizer.sanitize(config.name()), latency.toMillis(),
                                LogSanitizer.sanitize(throwable.getMessage()));
                        return DetectionResult.error(ip, config.name(), summarize(throwable));
                    }
                    return handleResponse(ip, response, latency);
                });
    }

    private HttpRequest buildRequest(String ip) {
        String encodedIp = URLEncoder.encode(ip, StandardCharsets.UTF_8);
        String url = config.urlTemplate().replace("{ip}", encodedIp);

        String apiKey = config.resolveApiKey();
        boolean useQueryParam = apiKey != null && !apiKey.isBlank()
                && config.apiKeyHeader() == null && config.apiKeyQueryParam() != null;
        if (useQueryParam) {
            String separator = url.contains("?") ? "&" : "?";
            url = url + separator + config.apiKeyQueryParam() + "=" + URLEncoder.encode(apiKey, StandardCharsets.UTF_8);
        }

        URI uri = URI.create(url);
        SsrfGuard.validate(uri);

        HttpRequest.Builder builder = HttpRequest.newBuilder(uri)
                .timeout(config.timeout())
                .header("Accept", "application/json")
                .header("User-Agent", "NetworkAntiVPN/1.0 (+JustSimpleNetworks)")
                .GET();

        if (apiKey != null && !apiKey.isBlank() && config.apiKeyHeader() != null) {
            builder.header(config.apiKeyHeader(), apiKey);
        }
        return builder.build();
    }

    private DetectionResult handleResponse(String ip, HttpResponse<String> response, Duration latency) {
        if (response.statusCode() == 429) {
            health.recordRateLimited(Duration.ofSeconds(30));
            return DetectionResult.error(ip, config.name(), "rate limited (HTTP 429)");
        }
        if (response.statusCode() / 100 != 2) {
            health.recordFailure("HTTP " + response.statusCode());
            return DetectionResult.error(ip, config.name(), "unexpected HTTP status " + response.statusCode());
        }

        JsonObject json;
        try {
            json = JsonParser.parseString(response.body()).getAsJsonObject();
        } catch (RuntimeException e) {
            health.recordFailure("malformed JSON response");
            return DetectionResult.error(ip, config.name(), "malformed JSON response");
        }

        DetectionResult.Builder builder = DetectionResult.builder(ip, config.name())
                .latency(latency)
                .confidence(1.0);

        List<String> evidence = new ArrayList<>();
        for (Map.Entry<String, String> mapping : config.fieldMapping().entrySet()) {
            applyMapping(builder, evidence, json, mapping.getKey(), mapping.getValue());
        }
        builder.evidence(evidence);

        health.recordSuccess(latency);
        return builder.build();
    }

    private void applyMapping(DetectionResult.Builder builder, List<String> evidence,
                               JsonObject json, String jsonPath, String field) {
        if (BOOLEAN_FIELDS.contains(field)) {
            Boolean value = JsonPathUtils.getBoolean(json, jsonPath);
            if (value == null) {
                return;
            }
            switch (field) {
                case "vpn" -> builder.vpn(value);
                case "proxy" -> builder.proxy(value);
                case "hosting" -> builder.hosting(value);
                case "datacenter" -> builder.datacenter(value);
                case "tor" -> builder.tor(value);
                case "residentialProxy" -> builder.residentialProxy(value);
                case "residential" -> builder.residential(value);
                default -> { /* unreachable, field validated against BOOLEAN_FIELDS above */ }
            }
            if (value) {
                evidence.add(field + "=true (" + jsonPath + ")");
            }
        } else if (STRING_FIELDS.contains(field)) {
            String value = JsonPathUtils.getString(json, jsonPath);
            if (value == null) {
                return;
            }
            switch (field) {
                case "country" -> builder.country(value);
                case "continent" -> builder.continent(value);
                case "isp" -> builder.isp(value);
                case "organization" -> builder.organization(value);
                case "hostname" -> builder.hostname(value);
                default -> { /* unreachable, field validated against STRING_FIELDS above */ }
            }
        } else if (INT_FIELDS.contains(field)) {
            Integer value = JsonPathUtils.getInt(json, jsonPath);
            if (value == null) {
                return;
            }
            if (field.equals("asn")) {
                builder.asn(value);
            } else {
                builder.riskScore(value);
            }
        } else {
            LOGGER.warn("Provider {} has an unknown fieldMapping target '{}' for path '{}' - ignoring",
                    LogSanitizer.sanitize(config.name()), LogSanitizer.sanitize(field), LogSanitizer.sanitize(jsonPath));
        }
    }

    private static String summarize(Throwable throwable) {
        Throwable root = throwable;
        while (root.getCause() != null && root.getCause() != root) {
            root = root.getCause();
        }
        return root.getClass().getSimpleName() + (root.getMessage() != null ? ": " + root.getMessage() : "");
    }
}
