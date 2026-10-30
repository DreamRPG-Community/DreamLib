package cn.mythicland.dreamlib.path;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.Objects;

/**
 * Resolves paths below an immutable, normalized root directory.
 *
 * @param root the normalized managed root directory
 */
public record SafePathResolver(Path root) {

    /**
     * Creates a resolver and normalizes its root directory to an absolute path.
     *
     * @param root the managed root directory
     */
    public SafePathResolver {
        root = Objects.requireNonNull(root, "root").toAbsolutePath().normalize();
    }

    /**
     * Ensures that the managed root exists as a real directory.
     *
     * @throws IOException if the root is a symbolic link or cannot be created
     */
    public void ensureRootDirectory() throws IOException {
        if (Files.isSymbolicLink(root)) {
            throw new IOException("Managed root cannot be a symbolic link: " + root);
        }
        Files.createDirectories(root);
    }

    /**
     * Validates and normalizes a logical name as one directory segment.
     *
     * @param logicalName the logical path segment to validate
     * @return the normalized single-segment name
     * @throws IllegalArgumentException if the name is blank, invalid, nested, absolute, or unsafe
     */
    public String normalizeSingleSegment(String logicalName) {
        if (logicalName == null || logicalName.isBlank()) {
            throw new IllegalArgumentException("Resource name cannot be blank");
        }
        if (!logicalName.equals(logicalName.trim())) {
            throw new IllegalArgumentException("Resource name cannot start or end with whitespace");
        }
        if (logicalName.contains("/") || logicalName.contains("\\")) {
            throw new IllegalArgumentException("Resource name must be a single directory segment");
        }
        if (logicalName.equals(".") || logicalName.equals("..")) {
            throw new IllegalArgumentException("Resource name cannot be a path traversal segment");
        }
        if (logicalName.chars().anyMatch(Character::isISOControl)) {
            throw new IllegalArgumentException("Resource name cannot contain control characters");
        }

        final Path segment;
        try {
            segment = Path.of(logicalName);
        } catch (InvalidPathException exception) {
            throw new IllegalArgumentException("Invalid resource name: " + logicalName, exception);
        }

        if (segment.isAbsolute() || segment.getNameCount() != 1) {
            throw new IllegalArgumentException("Resource name must be a relative single directory segment");
        }

        Path candidate = root.resolve(segment).normalize();
        if (!root.equals(candidate.getParent())) {
            throw new IllegalArgumentException("Resource name escapes the managed root: " + logicalName);
        }
        return segment.toString();
    }

    /**
     * Resolves a validated logical name below the managed root.
     *
     * @param logicalName the logical path segment to resolve
     * @return the resolved path below the root
     * @throws IllegalArgumentException if the name is invalid or escapes the root
     */
    public Path resolveSingleSegment(String logicalName) {
        return resolveRelative(normalizeSingleSegment(logicalName));
    }

    /**
     * Resolves a relative path below the root and rejects traversal and symbolic-link escapes.
     *
     * @param relativePath the relative path to resolve
     * @return the normalized path below the root
     * @throws IllegalArgumentException if the path is absolute, invalid, escapes the root, or
     *                                  traverses a symbolic link
     */
    public Path resolveRelative(String relativePath) {
        if (Files.isSymbolicLink(root)) {
            throw new IllegalArgumentException("Managed root cannot be a symbolic link: " + root);
        }
        if (relativePath == null || relativePath.isBlank()) {
            throw new IllegalArgumentException("Resource path cannot be blank");
        }
        if (relativePath.chars().anyMatch(Character::isISOControl)) {
            throw new IllegalArgumentException("Resource path cannot contain control characters");
        }

        final Path relative;
        try {
            relative = Path.of(relativePath);
        } catch (InvalidPathException exception) {
            throw new IllegalArgumentException("Invalid resource path: " + relativePath, exception);
        }
        if (relative.isAbsolute()) {
            throw new IllegalArgumentException("Resource path must be relative");
        }

        Path candidate = root.resolve(relative).normalize();
        if (!candidate.startsWith(root) || candidate.equals(root)) {
            throw new IllegalArgumentException("Resource path escapes the managed root: " + relativePath);
        }

        Path current = root;
        for (Path segment : root.relativize(candidate)) {
            current = current.resolve(segment);
            if (Files.isSymbolicLink(current)) {
                throw new IllegalArgumentException("Resource path traverses a symbolic link: " + relativePath);
            }
        }
        return candidate;
    }

    /**
     * Verifies that a path is an existing real directory without following symbolic links.
     *
     * @param path the path to verify
     * @throws IOException if the path is a symbolic link or is not a directory
     */
    public void requireRealDirectory(Path path) throws IOException {
        if (Files.isSymbolicLink(path)) {
            throw new IOException("Managed path cannot be a symbolic link: " + path);
        }
        if (!Files.isDirectory(path, LinkOption.NOFOLLOW_LINKS)) {
            throw new IOException("Managed path is not a directory: " + path);
        }
    }
}
