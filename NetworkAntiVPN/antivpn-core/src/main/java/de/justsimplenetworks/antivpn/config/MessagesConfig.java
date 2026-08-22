package de.justsimplenetworks.antivpn.config;

import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Wraps {@code messages.yml}. Every player/staff-facing string in this
 * plugin comes from here - nothing is hard-coded in Java, satisfying the
 * "keine Nachrichten hardcoden" requirement.
 */
public final class MessagesConfig {

    private final AtomicReference<YamlConfig> yaml;
    private final String prefix;

    public MessagesConfig(YamlConfig yaml) {
        this.yaml = new AtomicReference<>(yaml);
        this.prefix = yaml.getString("prefix", "&8[&bAntiVPN&8] &r");
    }

    public void reload(YamlConfig newYaml) {
        this.yaml.set(newYaml);
    }

    public String prefix() {
        return prefix;
    }

    /** Resolves {@code key} (dot-path into messages.yml) with {@code {placeholder}} substitution. */
    public String get(String key, Map<String, String> placeholders) {
        String template = yaml.get().getString(key, null);
        if (template == null) {
            return prefix + "&c(missing message: " + key + ")";
        }
        String message = prefix + template;
        for (Map.Entry<String, String> entry : placeholders.entrySet()) {
            message = message.replace("{" + entry.getKey() + "}", entry.getValue());
        }
        return message;
    }

    public String get(String key) {
        return get(key, Map.of());
    }
}
