package cn.mythicland.dreamlib.storage;

import org.bukkit.configuration.file.YamlConfiguration;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.Objects;

/**
 * Writes small plugin-owned text files through a same-directory temporary file.
 */
public final class AtomicFileStore {

    private AtomicFileStore() {
    }

    /**
     * Replaces a UTF-8 text file as one filesystem operation where supported.
     *
     * @param target  the target file
     * @param content the UTF-8 content
     * @throws IOException          if the parent or replacement cannot be written
     * @throws NullPointerException if an argument is null
     */
    public static void writeUtf8(Path target, String content) throws IOException {
        Objects.requireNonNull(content, "content");
        writeBytes(target, content.getBytes(StandardCharsets.UTF_8));
    }

    /**
     * Saves a YAML configuration through {@link #writeUtf8(Path, String)}.
     *
     * @param target        the target YAML file
     * @param configuration the configuration to save
     * @throws IOException          if the replacement cannot be written
     * @throws NullPointerException if an argument is null
     */
    public static void writeYaml(Path target, YamlConfiguration configuration) throws IOException {
        Objects.requireNonNull(configuration, "configuration");
        writeUtf8(target, configuration.saveToString());
    }

    /**
     * Captures the current bytes of a file for a later best-effort rollback.
     *
     * @param target the file to snapshot
     * @return an immutable snapshot indicating whether the file existed
     * @throws IOException if the file cannot be read
     */
    public static FileSnapshot snapshot(Path target) throws IOException {
        Objects.requireNonNull(target, "target");
        Path normalizedTarget = target.toAbsolutePath().normalize();
        if (!Files.exists(normalizedTarget)) return new FileSnapshot(false, new byte[0]);
        if (!Files.isRegularFile(normalizedTarget)) {
            throw new IOException("Target is not a regular file: " + target);
        }
        return new FileSnapshot(true, Files.readAllBytes(normalizedTarget));
    }

    /**
     * Restores a snapshot through the same atomic replacement path.
     *
     * @param target   the file to restore
     * @param snapshot the captured snapshot
     * @throws IOException if the snapshot cannot be restored
     */
    public static void restore(Path target, FileSnapshot snapshot) throws IOException {
        Objects.requireNonNull(snapshot, "snapshot");
        if (snapshot.existed()) {
            writeBytes(target, snapshot.bytes());
            return;
        }
        Files.deleteIfExists(target.toAbsolutePath().normalize());
    }

    private static void writeBytes(Path target, byte[] content) throws IOException {
        Objects.requireNonNull(target, "target");
        Objects.requireNonNull(content, "content");
        Path normalizedTarget = target.toAbsolutePath().normalize();
        Path parent = normalizedTarget.getParent();
        if (parent == null) throw new IOException("Target has no parent directory: " + target);
        Files.createDirectories(parent);

        Path temporary = Files.createTempFile(
                parent,
                "." + normalizedTarget.getFileName() + ".",
                ".tmp"
        );
        try {
            Files.write(
                    temporary,
                    content,
                    StandardOpenOption.TRUNCATE_EXISTING,
                    StandardOpenOption.WRITE
            );
            try {
                Files.move(
                        temporary,
                        normalizedTarget,
                        StandardCopyOption.ATOMIC_MOVE,
                        StandardCopyOption.REPLACE_EXISTING
                );
            } catch (AtomicMoveNotSupportedException exception) {
                Files.move(
                        temporary,
                        normalizedTarget,
                        StandardCopyOption.REPLACE_EXISTING
                );
            }
        } finally {
            Files.deleteIfExists(temporary);
        }
    }

    /**
     * Immutable rollback bytes.
     *
     * @param existed whether the file existed when captured
     * @param bytes   file bytes when it existed
     */
    public record FileSnapshot(boolean existed, byte[] bytes) {
        public FileSnapshot {
            Objects.requireNonNull(bytes, "bytes");
            bytes = bytes.clone();
        }

        @Override
        public byte[] bytes() {
            return bytes.clone();
        }
    }
}
