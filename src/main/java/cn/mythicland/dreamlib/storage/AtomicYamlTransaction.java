package cn.mythicland.dreamlib.storage;

import org.bukkit.configuration.file.YamlConfiguration;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Best-effort multi-file YAML transaction with atomic per-file replacement and rollback.
 */
public final class AtomicYamlTransaction implements AutoCloseable {

    private final Map<Path, AtomicFileStore.FileSnapshot> snapshots = new LinkedHashMap<>();
    private boolean committed;

    public AtomicYamlTransaction snapshot(Path target) throws IOException {
        snapshotFile(target);
        return this;
    }

    private void snapshotFile(Path target) throws IOException {
        Path normalized = Objects.requireNonNull(target, "target").toAbsolutePath().normalize();
        snapshots.putIfAbsent(normalized, AtomicFileStore.snapshot(normalized));
    }

    public AtomicYamlTransaction write(Path target, YamlConfiguration configuration) throws IOException {
        snapshotFile(target);
        AtomicYamlStore.write(target, configuration);
        return this;
    }

    public AtomicYamlTransaction writeText(Path target, String content) throws IOException {
        snapshotFile(target);
        AtomicFileStore.writeUtf8(target, Objects.requireNonNull(content, "content"));
        return this;
    }

    public void commit() {
        committed = true;
        snapshots.clear();
    }

    public void rollback() throws IOException {
        IOException failure = null;
        for (Map.Entry<Path, AtomicFileStore.FileSnapshot> entry : new ArrayList<>(snapshots.entrySet())) {
            try {
                AtomicFileStore.restore(entry.getKey(), entry.getValue());
            } catch (IOException exception) {
                if (failure == null) failure = exception;
                else failure.addSuppressed(exception);
            }
        }
        snapshots.clear();
        if (failure != null) throw failure;
    }

    @Override
    public void close() throws IOException {
        if (!committed) rollback();
    }
}
