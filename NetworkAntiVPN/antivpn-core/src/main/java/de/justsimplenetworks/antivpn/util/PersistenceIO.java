package de.justsimplenetworks.antivpn.util;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Small JSON list persistence helper used by the whitelist/blacklist/bypass
 * runtime stores to survive a restart without requiring a database. Writes
 * are atomic (write to a temp file, then move) so a crash mid-write never
 * corrupts the data file.
 * <p>
 * {@link Instant} and {@link UUID} get explicit string-based adapters:
 * Gson's default reflective adapter would otherwise try to reach into their
 * private fields, which the {@code java.base} module does not open to
 * reflection on modern JDKs.
 */
public final class PersistenceIO {

    private static final Logger LOGGER = LoggerFactory.getLogger(PersistenceIO.class);
    private static final Gson GSON = new GsonBuilder()
            .setPrettyPrinting()
            .registerTypeAdapter(Instant.class,
                    (com.google.gson.JsonSerializer<Instant>) (src, t, ctx) -> new com.google.gson.JsonPrimitive(src.toString()))
            .registerTypeAdapter(Instant.class,
                    (com.google.gson.JsonDeserializer<Instant>) (json, t, ctx) -> Instant.parse(json.getAsString()))
            .registerTypeAdapter(UUID.class,
                    (com.google.gson.JsonSerializer<UUID>) (src, t, ctx) -> new com.google.gson.JsonPrimitive(src.toString()))
            .registerTypeAdapter(UUID.class,
                    (com.google.gson.JsonDeserializer<UUID>) (json, t, ctx) -> UUID.fromString(json.getAsString()))
            .create();

    private PersistenceIO() {
    }

    public static <T> List<T> loadList(Path path, TypeToken<List<T>> type) {
        if (!Files.exists(path)) {
            return List.of();
        }
        try {
            String json = Files.readString(path, StandardCharsets.UTF_8);
            if (json.isBlank()) {
                return List.of();
            }
            List<T> result = GSON.fromJson(json, type.getType());
            return result == null ? List.of() : result;
        } catch (IOException | RuntimeException e) {
            LOGGER.warn("Failed to read {}, starting with an empty list", path, e);
            return List.of();
        }
    }

    public static <T> void saveList(Path path, List<T> entries) {
        try {
            Path parent = path.toAbsolutePath().getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            Path tempFile = Files.createTempFile(parent, path.getFileName().toString(), ".tmp");
            Files.writeString(tempFile, GSON.toJson(entries), StandardCharsets.UTF_8);
            Files.move(tempFile, path, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException e) {
            LOGGER.warn("Failed to persist {}", path, e);
        }
    }
}
