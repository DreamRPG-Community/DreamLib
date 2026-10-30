package cn.mythicland.dreamlib.storage;

import java.util.Objects;
import java.util.UUID;

/**
 * Thread-safe mutable holder for one immutable player snapshot and its persistence revision.
 *
 * @param <T> immutable snapshot type
 */
public final class VersionedPlayerSession<T> {

    private final UUID uniqueId;
    private T snapshot;
    private long databaseVersion;
    private long revision;
    private boolean dirty;

    /**
     * Creates a loaded player session.
     *
     * @param uniqueId        player UUID
     * @param snapshot        initial snapshot
     * @param databaseVersion database version from storage
     */
    public VersionedPlayerSession(UUID uniqueId, T snapshot, long databaseVersion) {
        this.uniqueId = Objects.requireNonNull(uniqueId, "uniqueId");
        this.snapshot = Objects.requireNonNull(snapshot, "snapshot");
        if (databaseVersion < 0L) throw new IllegalArgumentException("databaseVersion cannot be negative");
        this.databaseVersion = databaseVersion;
    }

    /**
     * Returns the current snapshot reference.
     *
     * @return current immutable snapshot
     */
    public synchronized T snapshot() {
        return snapshot;
    }

    /**
     * Replaces the snapshot and marks this session dirty.
     *
     * @param nextSnapshot next immutable snapshot
     */
    public synchronized void replace(T nextSnapshot) {
        snapshot = Objects.requireNonNull(nextSnapshot, "nextSnapshot");
        revision++;
        dirty = true;
    }

    /**
     * Captures a persistence candidate without clearing its dirty state.
     *
     * @return immutable save candidate
     */
    public synchronized SaveCandidate<T> saveCandidate() {
        return new SaveCandidate<>(uniqueId, snapshot, databaseVersion, revision, dirty);
    }

    /**
     * Marks a candidate as persisted.
     *
     * @param candidate   candidate returned by {@link #saveCandidate()}
     * @param nextVersion newly persisted database version
     */
    public synchronized void completeSave(SaveCandidate<T> candidate, long nextVersion) {
        Objects.requireNonNull(candidate, "candidate");
        if (!uniqueId.equals(candidate.uniqueId())) {
            throw new IllegalArgumentException("Save candidate belongs to another player");
        }
        if (nextVersion <= candidate.databaseVersion()) {
            throw new IllegalArgumentException("nextVersion must advance the database version");
        }
        databaseVersion = nextVersion;
        if (revision == candidate.revision()) dirty = false;
    }

    /**
     * Returns whether a newer snapshot needs persistence.
     *
     * @return true when the session is dirty
     */
    public synchronized boolean isDirty() {
        return dirty;
    }

    /**
     * Immutable persistence candidate.
     *
     * @param uniqueId        player UUID
     * @param snapshot        snapshot to persist
     * @param databaseVersion expected database version
     * @param revision        in-memory revision
     * @param dirty           whether persistence is required
     * @param <T>             snapshot type
     */
    public record SaveCandidate<T>(
            UUID uniqueId,
            T snapshot,
            long databaseVersion,
            long revision,
            boolean dirty
    ) {

        /**
         * Validates candidate values.
         */
        @SuppressWarnings("DataFlowIssue")
        public SaveCandidate {
            uniqueId = Objects.requireNonNull(uniqueId, "uniqueId");
            snapshot = Objects.requireNonNull(snapshot, "snapshot");
            if (databaseVersion < 0L) throw new IllegalArgumentException("databaseVersion cannot be negative");
            if (revision < 0L) throw new IllegalArgumentException("revision cannot be negative");
        }
    }
}
