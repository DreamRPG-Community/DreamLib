package cn.mythicland.dreamlib.path;

import java.nio.file.Path;
import java.util.Objects;

/**
 * Facade for safe plugin-managed path resolution.
 */
public final class PathService {

    /**
     * Creates a resolver for a root directory.
     *
     * @param root managed root
     * @return resolver
     */
    public ManagedPathResolver managed(Path root) {
        return new ManagedPathResolver(Objects.requireNonNull(root, "root"));
    }
}
