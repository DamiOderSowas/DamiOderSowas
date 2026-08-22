package de.justsimplenetworks.antivpn.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

/**
 * Copies the bundled default {@code config.yml}/{@code messages.yml} into a
 * plugin's data directory the first time it starts, then loads whatever is
 * on disk (so operator edits always win on subsequent starts/reloads).
 */
public final class ConfigLoader {

    private static final Logger LOGGER = LoggerFactory.getLogger(ConfigLoader.class);

    private ConfigLoader() {
    }

    public static YamlConfig loadOrCreate(Path dataDirectory, String fileName) {
        try {
            Files.createDirectories(dataDirectory);
            Path target = dataDirectory.resolve(fileName);
            if (!Files.exists(target)) {
                copyDefault(fileName, target);
            }
            return YamlConfig.load(target);
        } catch (IOException e) {
            LOGGER.error("Failed to load {}, falling back to bundled defaults", fileName, e);
            return loadBundledDefault(fileName);
        }
    }

    private static void copyDefault(String fileName, Path target) throws IOException {
        try (InputStream in = ConfigLoader.class.getClassLoader().getResourceAsStream(fileName)) {
            if (in == null) {
                throw new IOException("No bundled default resource named " + fileName);
            }
            Files.copy(in, target, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private static YamlConfig loadBundledDefault(String fileName) {
        try (InputStream in = ConfigLoader.class.getClassLoader().getResourceAsStream(fileName)) {
            if (in == null) {
                LOGGER.error("No bundled default resource named {} either - using an empty config", fileName);
                return YamlConfig.load(new java.io.ByteArrayInputStream(new byte[0]));
            }
            return YamlConfig.load(in);
        } catch (IOException e) {
            LOGGER.error("Failed to load bundled default {}", fileName, e);
            return YamlConfig.load(new java.io.ByteArrayInputStream(new byte[0]));
        }
    }
}
