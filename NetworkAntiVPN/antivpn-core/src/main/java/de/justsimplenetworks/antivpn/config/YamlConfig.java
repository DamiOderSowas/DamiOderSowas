package de.justsimplenetworks.antivpn.config;

import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.SafeConstructor;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * A thin, defensive wrapper around a SnakeYAML-parsed {@code Map<String, Object>}
 * tree, offering dot-path typed accessors with defaults. Deliberately not a
 * JavaBean-mapped config: {@code config.yml} has too many independent,
 * versioned sections for hand-written bean classes to stay pleasant to
 * maintain, and this wrapper degrades gracefully (returns the default) if a
 * key is missing or malformed instead of throwing during startup.
 * <p>
 * Uses SnakeYAML's {@link SafeConstructor}, so a hostile {@code config.yml}
 * can never trigger arbitrary type instantiation.
 */
public final class YamlConfig {

    private final Map<String, Object> root;

    private YamlConfig(Map<String, Object> root) {
        this.root = root;
    }

    public static YamlConfig load(Path file) throws IOException {
        try (InputStream in = Files.newInputStream(file)) {
            return load(in);
        }
    }

    public static YamlConfig load(InputStream in) {
        Yaml yaml = new Yaml(new SafeConstructor(new LoaderOptions()));
        Object data = yaml.load(in);
        return new YamlConfig(asMap(data));
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> asMap(Object data) {
        if (data instanceof Map<?, ?> map) {
            return (Map<String, Object>) map;
        }
        return new LinkedHashMap<>();
    }

    private Object resolve(String path) {
        String[] segments = path.split("\\.");
        Object current = root;
        for (String segment : segments) {
            if (!(current instanceof Map<?, ?> map)) {
                return null;
            }
            current = map.get(segment);
        }
        return current;
    }

    public YamlConfig section(String path) {
        Object value = resolve(path);
        return new YamlConfig(asMap(value));
    }

    public Set<String> keys(String path) {
        Object value = resolve(path);
        return value instanceof Map<?, ?> map
                ? map.keySet().stream().map(String::valueOf).collect(java.util.stream.Collectors.toCollection(java.util.LinkedHashSet::new))
                : Set.of();
    }

    public boolean getBoolean(String path, boolean fallback) {
        Object value = resolve(path);
        return value instanceof Boolean b ? b : fallback;
    }

    public int getInt(String path, int fallback) {
        Object value = resolve(path);
        return value instanceof Number n ? n.intValue() : fallback;
    }

    public double getDouble(String path, double fallback) {
        Object value = resolve(path);
        return value instanceof Number n ? n.doubleValue() : fallback;
    }

    public long getLong(String path, long fallback) {
        Object value = resolve(path);
        return value instanceof Number n ? n.longValue() : fallback;
    }

    public String getString(String path, String fallback) {
        Object value = resolve(path);
        return value == null ? fallback : String.valueOf(value);
    }

    @SuppressWarnings("unchecked")
    public List<String> getStringList(String path) {
        Object value = resolve(path);
        if (!(value instanceof List<?> list)) {
            return List.of();
        }
        return list.stream().map(String::valueOf).toList();
    }

    public List<Integer> getIntList(String path) {
        Object value = resolve(path);
        if (!(value instanceof List<?> list)) {
            return List.of();
        }
        return list.stream()
                .filter(Number.class::isInstance)
                .map(o -> ((Number) o).intValue())
                .toList();
    }

    @SuppressWarnings("unchecked")
    public List<Map<String, Object>> getMapList(String path) {
        Object value = resolve(path);
        if (!(value instanceof List<?> list)) {
            return List.of();
        }
        return list.stream()
                .filter(Map.class::isInstance)
                .map(o -> (Map<String, Object>) o)
                .toList();
    }

    public Map<String, String> getStringMap(String path) {
        Object value = resolve(path);
        if (!(value instanceof Map<?, ?> map)) {
            return Map.of();
        }
        Map<String, String> result = new LinkedHashMap<>();
        for (Map.Entry<?, ?> entry : map.entrySet()) {
            if (entry.getValue() != null) {
                result.put(String.valueOf(entry.getKey()), String.valueOf(entry.getValue()));
            }
        }
        return result;
    }

    public Map<String, Integer> getIntMap(String path) {
        Object value = resolve(path);
        if (!(value instanceof Map<?, ?> map)) {
            return Map.of();
        }
        Map<String, Integer> result = new LinkedHashMap<>();
        for (Map.Entry<?, ?> entry : map.entrySet()) {
            if (entry.getValue() instanceof Number n) {
                result.put(String.valueOf(entry.getKey()), n.intValue());
            }
        }
        return result;
    }

    public Map<String, Object> raw() {
        return root;
    }

    public boolean isEmpty() {
        return root.isEmpty();
    }

    public static String readClasspathResourceAsString(String resourcePath) throws IOException {
        try (InputStream in = YamlConfig.class.getClassLoader().getResourceAsStream(resourcePath)) {
            if (in == null) {
                throw new IOException("Missing bundled resource: " + resourcePath);
            }
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
