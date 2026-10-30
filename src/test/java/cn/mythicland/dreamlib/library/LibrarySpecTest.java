package cn.mythicland.dreamlib.library;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Verifies immutable library coordinates and SHA-256 cache validation primitives.
 */
class LibrarySpecTest {

    @Test
    void mavenCoordinateBuildsTheExpectedRepositoryPath() {
        LibrarySpec specification = new LibrarySpec(
                "org.xerial:sqlite-jdbc:3.46.1.3",
                "sqlite-jdbc-3.46.1.3.jar",
                "4a4832720a65eaf7f4d6fd7ede52087b994dc5633c076f9e994dc0c8b4b0b4fa"
        );

        assertEquals(
                "org/xerial/sqlite-jdbc/3.46.1.3/sqlite-jdbc-3.46.1.3.jar",
                specification.repositoryPath()
        );
    }

    @Test
    void unsafeCoordinatePathSegmentsAreRejected() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new LibrarySpec(
                        "org.example:../sqlite:1.0.0",
                        "sqlite.jar",
                        "0000000000000000000000000000000000000000000000000000000000000000"
                )
        );
    }

    @Test
    void sha256MatchesTheBytesUsedByTheLibraryVerifier(@TempDir Path temporaryDirectory) throws Exception {
        Path artifact = temporaryDirectory.resolve("artifact.jar");
        Files.writeString(artifact, "hello");

        assertEquals(
                "2cf24dba5fb0a30e26e83b2ac5b9e29e1b161e5c1fa7425e73043362938b9824",
                LibraryService.sha256(artifact)
        );
    }

    @Test
    void loadResultCopiesItsJarList() throws Exception {
        Path artifact = Path.of("artifact.jar");
        try (LibraryLoadResult result = new LibraryLoadResult(
                new java.util.ArrayList<>(List.of(artifact)),
                ClassLoader.getSystemClassLoader()
        )) {
            assertThrows(UnsupportedOperationException.class, () -> result.jars().add(Path.of("other.jar")));
            assertEquals(List.of(artifact), result.jars());
        }
    }
}
