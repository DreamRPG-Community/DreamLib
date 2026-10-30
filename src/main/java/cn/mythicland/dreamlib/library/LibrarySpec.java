package cn.mythicland.dreamlib.library;

import java.util.Locale;
import java.util.Objects;

/**
 * Describes one immutable runtime library artifact.
 *
 * @param coordinate Maven-style {@code groupId:artifactId:version} coordinate
 * @param fileName   single-segment JAR file name
 * @param sha256     expected lowercase SHA-256 digest
 */
public record LibrarySpec(
        String coordinate,
        String fileName,
        String sha256
) {

    /**
     * Validates and normalizes a library specification.
     */
    public LibrarySpec {
        coordinate = Objects.requireNonNull(coordinate, "coordinate").trim();
        fileName = Objects.requireNonNull(fileName, "fileName").trim();
        sha256 = Objects.requireNonNull(sha256, "sha256").trim().toLowerCase(Locale.ROOT);

        String[] coordinateParts = coordinate.split(":", -1);
        if (coordinateParts.length != 3 || containsBlank(coordinateParts)) {
            throw new IllegalArgumentException(
                    "Library coordinate must use groupId:artifactId:version: " + coordinate
            );
        }
        if (!containsSafeCoordinateSegments(coordinateParts)) {
            throw new IllegalArgumentException("Library coordinate contains an unsafe path segment: " + coordinate);
        }
        if (fileName.isBlank() || !fileName.endsWith(".jar") || containsPathSeparator(fileName)) {
            throw new IllegalArgumentException("Library fileName must be a single JAR name: " + fileName);
        }
        if (!sha256.matches("[0-9a-f]{64}")) {
            throw new IllegalArgumentException("Library SHA-256 must contain exactly 64 hexadecimal characters");
        }
    }

    private static boolean containsBlank(String[] values) {
        for (String value : values) {
            if (value.isBlank()) return true;
        }
        return false;
    }

    private static boolean containsPathSeparator(String value) {
        return value.indexOf('/') >= 0 || value.indexOf('\\') >= 0 || value.contains("..")
                || value.indexOf('\u0000') >= 0;
    }

    private static boolean containsSafeCoordinateSegments(String[] values) {
        for (String value : values) {
            if (!value.matches("[A-Za-z0-9_.+\\-]+")) return false;
        }
        return true;
    }

    /**
     * Returns the Maven group identifier.
     *
     * @return group identifier
     */
    public String groupId() {
        return coordinate.split(":", -1)[0];
    }

    /**
     * Returns the Maven artifact identifier.
     *
     * @return artifact identifier
     */
    public String artifactId() {
        return coordinate.split(":", -1)[1];
    }

    /**
     * Returns the Maven version.
     *
     * @return artifact version
     */
    public String version() {
        return coordinate.split(":", -1)[2];
    }

    /**
     * Returns the path below a Maven repository root.
     *
     * @return repository-relative artifact path
     */
    public String repositoryPath() {
        return groupId().replace('.', '/')
                + "/" + artifactId()
                + "/" + version()
                + "/" + fileName;
    }
}
