package cn.mythicland.dreamlib.text;

import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.Map;
import java.util.Objects;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Reads legacy nested YAML messages without exposing mutable configuration state to callers.
 *
 * <p>Messages are deliberately rendered with literal replacement rather than regular
 * expressions. Existing ILS placeholders may contain player names, item names, or entity names
 * containing regular-expression characters.</p>
 */
public final class YamlMessageBundle {

    private static final String FILE_VERSION_PATH = "FileVersion";

    private final YamlConfiguration configuration;
    private final int fileVersion;
    private final boolean compatible;

    private YamlMessageBundle(YamlConfiguration configuration, int fileVersion, boolean compatible) {
        this.configuration = configuration;
        this.fileVersion = fileVersion;
        this.compatible = compatible;
    }

    /**
     * Loads a YAML message file.
     *
     * @param file           message file
     * @param minimumVersion minimum accepted FileVersion
     * @param logger         logger used for a stale version notice
     * @return loaded message bundle
     * @throws IOException if the file cannot be read or parsed
     */
    public static YamlMessageBundle load(Path file, int minimumVersion, Logger logger)
            throws IOException {
        Objects.requireNonNull(file, "file");
        Objects.requireNonNull(logger, "logger");
        try (InputStream input = java.nio.file.Files.newInputStream(file)) {
            return load(input, file.toString(), minimumVersion, logger);
        }
    }

    /**
     * Loads a message bundle from a plugin resource or another UTF-8 stream.
     *
     * @param input          source stream; ownership is transferred to this method
     * @param sourceName     source description used in diagnostics
     * @param minimumVersion minimum accepted FileVersion
     * @param logger         logger used for a stale version notice
     * @return loaded message bundle
     * @throws IOException if the stream cannot be read or parsed
     */
    public static YamlMessageBundle load(
            InputStream input,
            String sourceName,
            int minimumVersion,
            Logger logger
    ) throws IOException {
        Objects.requireNonNull(input, "input");
        Objects.requireNonNull(sourceName, "sourceName");
        Objects.requireNonNull(logger, "logger");
        YamlConfiguration configuration = new YamlConfiguration();
        try (Reader reader = new InputStreamReader(input, StandardCharsets.UTF_8)) {
            configuration.load(reader);
        } catch (IOException | InvalidConfigurationException exception) {
            throw new IOException("Unable to load message file " + sourceName, exception);
        }

        int fileVersion = configuration.getInt(FILE_VERSION_PATH, 0);
        boolean compatible = fileVersion >= minimumVersion;
        if (!compatible) {
            logger.log(
                    Level.WARNING,
                    "Message file {0} is older than the required version {1} (found {2}).",
                    new Object[]{sourceName, minimumVersion, fileVersion}
            );
        }
        return new YamlMessageBundle(configuration, fileVersion, compatible);
    }

    /**
     * Returns whether the file meets the requested minimum version.
     */
    public boolean compatible() {
        return compatible;
    }

    /**
     * Returns the version declared by the message file.
     */
    public int fileVersion() {
        return fileVersion;
    }

    /**
     * Renders a message with literal placeholder replacement and legacy colour conversion.
     *
     * @param key          nested YAML key
     * @param placeholders placeholder values without braces
     * @param fallback     value returned when the key is absent or blank
     * @return translated message
     */
    public String render(String key, Map<String, ?> placeholders, String fallback) {
        Objects.requireNonNull(key, "key");
        Objects.requireNonNull(placeholders, "placeholders");
        String message = compatible() ? configuration.getString(key, fallback) : fallback;
        if (message == null || message.isBlank()) message = fallback;
        if (message == null) return "";

        String rendered = message;
        for (Map.Entry<String, ?> entry : placeholders.entrySet()) {
            String token = "{" + entry.getKey() + "}";
            String value = Objects.toString(entry.getValue(), "");
            rendered = rendered.replace(token, value);
        }
        return LegacyText.colorize(rendered);
    }
}
