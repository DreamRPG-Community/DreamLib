package cn.mythicland.dreamlib.database;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Verifies migration descriptors reject ambiguous lifecycle input.
 */
class MigrationSpecTest {

    @Test
    void migrationVersionMustBePositive() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new MigrationSpec(0, "db/V0.sql")
        );
    }

    @Test
    void migrationResourcePathIsTrimmedButPreservedAsAResourceName() {
        MigrationSpec specification = new MigrationSpec(1, " db/V1.sql ");

        assertEquals("db/V1.sql", specification.resourcePath());
    }
}
