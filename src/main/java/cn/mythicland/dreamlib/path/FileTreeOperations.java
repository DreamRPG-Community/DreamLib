package cn.mythicland.dreamlib.path;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;

/**
 * Provides file-tree operations with symbolic-link-safe traversal.
 */
public final class FileTreeOperations {

    private FileTreeOperations() {
    }

    /**
     * Deletes a path and all of its descendants without following symbolic links.
     * A symbolic link is deleted as one entry rather than traversed.
     *
     * @param path the file, symbolic link, or directory to delete
     * @return the number of deleted file-system entries
     * @throws IOException if a file-system operation fails
     */
    public static int deleteRecursively(Path path) throws IOException {
        Objects.requireNonNull(path, "path");
        if (Files.isSymbolicLink(path) || !Files.isDirectory(path, LinkOption.NOFOLLOW_LINKS)) {
            return deleteEntry(path);
        }

        AtomicInteger deletedEntries = new AtomicInteger();
        Files.walkFileTree(path, new SimpleFileVisitor<>() {
            @Override
            public @Nonnull FileVisitResult visitFile(
                    @Nonnull Path file,
                    @Nonnull BasicFileAttributes attributes
            ) throws IOException {
                deletedEntries.addAndGet(deleteEntry(file));
                return FileVisitResult.CONTINUE;
            }

            @Override
            public @Nonnull FileVisitResult postVisitDirectory(
                    @Nonnull Path directory,
                    @Nullable IOException exception
            ) throws IOException {
                if (exception != null) throw exception;
                deletedEntries.addAndGet(deleteEntry(directory));
                return FileVisitResult.CONTINUE;
            }
        });
        return deletedEntries.get();
    }

    /**
     * Removes empty descendant directories while retaining the supplied root directory.
     * Symbolic links are never followed or removed by this operation.
     *
     * @param rootDirectory the real directory whose empty descendants should be removed
     * @return the number of removed directories
     * @throws IOException if the root is not a real directory or a file-system operation fails
     */
    public static int removeEmptyDirectories(Path rootDirectory) throws IOException {
        Objects.requireNonNull(rootDirectory, "rootDirectory");
        if (Files.isSymbolicLink(rootDirectory)) {
            throw new IOException("Root directory cannot be a symbolic link: " + rootDirectory);
        }
        if (!Files.isDirectory(rootDirectory, LinkOption.NOFOLLOW_LINKS)) {
            throw new IOException("Root path is not a directory: " + rootDirectory);
        }

        AtomicInteger deletedDirectories = new AtomicInteger();
        Files.walkFileTree(rootDirectory, new SimpleFileVisitor<>() {
            @Override
            public @Nonnull FileVisitResult postVisitDirectory(
                    @Nonnull Path directory,
                    @Nullable IOException exception
            ) throws IOException {
                if (exception != null) throw exception;
                if (directory.equals(rootDirectory) || Files.isSymbolicLink(directory)) {
                    return FileVisitResult.CONTINUE;
                }

                try (Stream<Path> children = Files.list(directory)) {
                    if (children.findAny().isEmpty()) deletedDirectories.addAndGet(deleteEntry(directory));
                }
                return FileVisitResult.CONTINUE;
            }
        });
        return deletedDirectories.get();
    }

    private static int deleteEntry(Path path) throws IOException {
        return Files.deleteIfExists(path) ? 1 : 0;
    }
}
