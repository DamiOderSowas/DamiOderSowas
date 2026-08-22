package de.justsimplenetworks.antivpn.providers;

/** What kind of signal a {@link DetectionProvider} is able to report. Used for capability-aware routing. */
public enum ProviderCapability {
    VPN,
    PROXY,
    HOSTING,
    DATACENTER,
    TOR,
    RESIDENTIAL_PROXY,
    RESIDENTIAL,
    COUNTRY,
    ASN,
    ISP,
    ORGANIZATION,
    REPUTATION
}
