package cn.mythicland.dreamlib.storage;

import org.bukkit.configuration.file.YamlConfiguration;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Map;
import java.util.Objects;

/**
 * Generic YAML persistence facade backed by {@link AtomicFileStore}.
 */
public final class AtomicYamlStore {

    private AtomicYamlStore() {
    }

    /**
     * Writes an existing Bukkit YAML configuration atomically.
     *
     * @param target        target file
     * @param configuration YAML configuration
     * @throws IOException if the file cannot be replaced
     */
    public static void write(Path target, YamlConfiguration configuration) throws IOException {
        AtomicFileStore.writeYaml(target, Objects.requireNonNull(configuration, "configuration"));
    }

    /**
     * Writes a YAML tree atomically.
     *
     * @param target target file
     * @param values top-level values
     * @throws IOException if the file cannot be replaced
     */
    public static void write(Path target, Map<String, ?> values) throws IOException {
        Objects.requireNonNull(values, "values");
        YamlConfiguration configuration = new YamlConfiguration();
        values.forEach((key, value) -> configuration.set(key, YamlTree.mutable(value)));
        write(target, configuration);
    }
}
