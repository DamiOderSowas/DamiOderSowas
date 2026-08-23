package de.justsimplenetworks.antivpn.util;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

/**
 * Minimal dot-notation JSON path resolver, e.g. {@code "security.vpn"} on
 * {@code {"security":{"vpn":true}}}. Deliberately does not support array
 * indexing - providers configured through {@code ConfigurableHttpDetectionProvider}
 * are expected to return flat/nested objects, not arrays, for the fields
 * that matter to detection.
 */
public final class JsonPathUtils {

    private JsonPathUtils() {
    }

    /** Returns the element at {@code path}, or {@code null} if any segment is missing. */
    public static JsonElement get(JsonObject root, String path) {
        JsonElement current = root;
        for (String segment : path.split("\\.")) {
            if (current == null || !current.isJsonObject()) {
                return null;
            }
            current = current.getAsJsonObject().get(segment);
        }
        return current;
    }

    public static Boolean getBoolean(JsonObject root, String path) {
        JsonElement element = get(root, path);
        if (element == null || element.isJsonNull()) {
            return null;
        }
        if (element.isJsonPrimitive() && element.getAsJsonPrimitive().isBoolean()) {
            return element.getAsBoolean();
        }
        if (element.isJsonPrimitive() && element.getAsJsonPrimitive().isNumber()) {
            return element.getAsDouble() != 0;
        }
        if (element.isJsonPrimitive() && element.getAsJsonPrimitive().isString()) {
            String s = element.getAsString().trim().toLowerCase(java.util.Locale.ROOT);
            if (s.equals("true") || s.equals("yes") || s.equals("1")) return true;
            if (s.equals("false") || s.equals("no") || s.equals("0")) return false;
        }
        return null;
    }

    public static String getString(JsonObject root, String path) {
        JsonElement element = get(root, path);
        if (element == null || element.isJsonNull() || !element.isJsonPrimitive()) {
            return null;
        }
        return element.getAsString();
    }

    public static Integer getInt(JsonObject root, String path) {
        JsonElement element = get(root, path);
        if (element == null || element.isJsonNull() || !element.isJsonPrimitive()
                || !element.getAsJsonPrimitive().isNumber()) {
            return null;
        }
        try {
            return element.getAsInt();
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
