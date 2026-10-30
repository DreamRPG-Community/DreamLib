package cn.mythicland.dreamlib.library;

import java.io.IOException;
import java.net.URLClassLoader;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;

/**
 * Describes the verified libraries loaded into one plugin-owned dependency class loader.
 *
 * @param jars        verified JAR paths in load order
 * @param classLoader class loader that received the JAR URLs, with the plugin loader as parent
 */
public record LibraryLoadResult(
        List<Path> jars,
        ClassLoader classLoader
) implements AutoCloseable {

    /**
     * Creates an immutable result snapshot.
     */
    @SuppressWarnings("DataFlowIssue")
    public LibraryLoadResult {
        jars = List.copyOf(Objects.requireNonNull(jars, "jars"));
        classLoader = Objects.requireNonNull(classLoader, "classLoader");
    }

    /**
     * Closes the dependency class loader when it owns closeable JAR resources.
     *
     * @throws IOException when the class loader cannot release one of its JAR files
     */
    @Override
    public void close() throws IOException {
        if (!(classLoader instanceof URLClassLoader urlClassLoader)) return;
        urlClassLoader.close();
    }
}
