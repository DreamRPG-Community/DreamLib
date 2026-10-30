package cn.mythicland.dreamlib.storage;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Verifies that a newer in-memory player snapshot remains dirty while an older save is running.
 */
class VersionedPlayerSessionTest {

    @Test
    void newerSnapshotRemainsDirtyAfterOlderCandidateIsPersisted() {
        VersionedPlayerSession<String> session = new VersionedPlayerSession<>(
                UUID.randomUUID(),
                "first",
                0L
        );
        session.replace("second");
        VersionedPlayerSession.SaveCandidate<String> firstCandidate = session.saveCandidate();
        session.replace("third");

        session.completeSave(firstCandidate, 1L);

        assertTrue(session.isDirty());
        VersionedPlayerSession.SaveCandidate<String> secondCandidate = session.saveCandidate();
        session.completeSave(secondCandidate, 2L);
        assertFalse(session.isDirty());
    }
}
