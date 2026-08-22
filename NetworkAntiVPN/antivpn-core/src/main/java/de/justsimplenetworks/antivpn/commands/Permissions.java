package de.justsimplenetworks.antivpn.commands;

/** Permission node constants; register all of these in each platform's plugin metadata. */
public final class Permissions {

    private Permissions() {
    }

    public static final String ADMIN = "network.antivpn.admin";
    public static final String CHECK = "network.antivpn.check";
    public static final String INSPECT = "network.antivpn.inspect";
    public static final String DEBUG = "network.antivpn.debug";
    public static final String RELOAD = "network.antivpn.reload";
    public static final String STATUS = "network.antivpn.status";
    public static final String PROVIDERS = "network.antivpn.providers";
    public static final String CACHE = "network.antivpn.cache";
    public static final String WHITELIST = "network.antivpn.whitelist";
    public static final String BLACKLIST = "network.antivpn.blacklist";
    public static final String BYPASS = "network.antivpn.bypass";
    public static final String TRUST = "network.antivpn.trust";
}
