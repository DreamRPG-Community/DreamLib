package cn.mythicland.dreamlib.storage;

import org.bukkit.configuration.file.YamlConfiguration;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;
import java.util.Optional;

/**
 * Facade for shared atomic and defensive file operations.
 */
public final class StorageService {

    /**
     * Writes UTF-8 text atomically.
     *
     * @param target  target file
     * @param content content
     * @throws IOException if the write fails
     */
    public void writeUtf8(Path target, String content) throws IOException {
        AtomicFileStore.writeUtf8(target, content);
    }

    /**
     * Writes Bukkit YAML atomically.
     *
     * @param target        target file
     * @param configuration configuration
     * @throws IOException if the write fails
     */
    public void writeYaml(Path target, YamlConfiguration configuration) throws IOException {
        AtomicYamlStore.write(target, configuration);
    }

    /**
     * Reads a UTF-8 file if it exists.
     *
     * @param target file
     * @return file content, or an empty optional when absent
     * @throws IOException if reading fails
     */
    public Optional<String> readUtf8(Path target) throws IOException {
        Objects.requireNonNull(target, "target");
        if (!Files.exists(target)) return Optional.empty();
        return Optional.of(Files.readString(target, StandardCharsets.UTF_8));
    }

    /**
     * Captures a file for rollback.
     *
     * @param target target file
     * @return snapshot
     * @throws IOException if reading fails
     */
    public AtomicFileStore.FileSnapshot snapshot(Path target) throws IOException {
        return AtomicFileStore.snapshot(target);
    }

    /**
     * Restores a previously captured file snapshot.
     *
     * @param target   target file
     * @param snapshot snapshot
     * @throws IOException if restoration fails
     */
    public void restore(Path target, AtomicFileStore.FileSnapshot snapshot) throws IOException {
        AtomicFileStore.restore(target, snapshot);
    }

    /**
     * Starts a best-effort multi-file YAML transaction.
     */
    public AtomicYamlTransaction transaction() {
        return new AtomicYamlTransaction();
    }

    /**
     * Returns a stable fingerprint for YAML files below a managed root.
     */
    public String fingerprint(Path root) throws IOException {
        return FileTreeFingerprint.of(root);
    }
}
