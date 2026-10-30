package cn.mythicland.dreamlib.database;

import java.util.Objects;

/**
 * One ordered SQL migration resource.
 *
 * @param version      positive migration version
 * @param resourcePath plugin resource path containing SQL
 */
public record MigrationSpec(
        int version,
        String resourcePath
) {

    /**
     * Validates one migration descriptor.
     */
    public MigrationSpec {
        if (version < 1) throw new IllegalArgumentException("Migration version must be positive");
        resourcePath = Objects.requireNonNull(resourcePath, "resourcePath").trim();
        if (resourcePath.isBlank()) throw new IllegalArgumentException("Migration resourcePath cannot be blank");
    }
}
