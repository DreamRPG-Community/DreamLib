package cn.mythicland.dreamlib.path;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Objects;

/**
 * Generic path service used by plugins that own a directory tree.
 *
 * <p>The resolver deliberately exposes only safe paths below its root. It does not know what a
 * plugin stores there and therefore has no domain-specific naming rules.</p>
 */
public final class ManagedPathResolver {

    private final SafePathResolver delegate;

    /**
     * Creates a resolver for a managed root.
     *
     * @param root the managed root
     */
    public ManagedPathResolver(Path root) {
        this.delegate = new SafePathResolver(Objects.requireNonNull(root, "root"));
    }

    /**
     * Returns the normalized absolute root.
     *
     * @return root path
     */
    public Path root() {
        return delegate.root();
    }

    /**
     * Ensures the root is a real directory.
     *
     * @throws IOException if the root cannot be created or is a symbolic link
     */
    public void ensureRootDirectory() throws IOException {
        delegate.ensureRootDirectory();
    }

    /**
     * Resolves a safe relative path below the root.
     *
     * @param relativePath relative path
     * @return resolved path
     */
    public Path resolve(String relativePath) {
        return delegate.resolveRelative(relativePath);
    }

    /**
     * Resolves a safe single-segment name below the root.
     *
     * @param resourceName resource name
     * @return resolved path
     */
    public Path resolveSingleSegment(String resourceName) {
        return delegate.resolveSingleSegment(resourceName);
    }
}
