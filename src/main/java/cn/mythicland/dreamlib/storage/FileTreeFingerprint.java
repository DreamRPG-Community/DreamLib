package cn.mythicland.dreamlib.storage;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Locale;
import java.util.stream.Stream;

/**
 * Stable lightweight fingerprint for a managed YAML file tree.
 */
public final class FileTreeFingerprint {

    private FileTreeFingerprint() {
    }

    public static String of(Path root) throws IOException {
        if (root == null || !Files.isDirectory(root, LinkOption.NOFOLLOW_LINKS)) return "";
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            try (Stream<Path> paths = Files.walk(root)) {
                paths.filter(path -> Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS))
                        .filter(FileTreeFingerprint::isYaml)
                        .sorted()
                        .forEach(path -> update(digest, root, path));
            }
            return HexFormat.of().formatHex(digest.digest());
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    private static void update(MessageDigest digest, Path root, Path path) {
        try {
            digest.update(root.relativize(path).toString().replace('\\', '/').getBytes(StandardCharsets.UTF_8));
            digest.update(Long.toString(Files.size(path)).getBytes(StandardCharsets.UTF_8));
            digest.update(Long.toString(Files.getLastModifiedTime(path, LinkOption.NOFOLLOW_LINKS).toMillis())
                    .getBytes(StandardCharsets.UTF_8));
        } catch (IOException exception) {
            throw new IllegalStateException("Unable to fingerprint " + path, exception);
        }
    }

    private static boolean isYaml(Path path) {
        String name = path.getFileName().toString().toLowerCase(Locale.ROOT);
        return name.endsWith(".yml") || name.endsWith(".yaml");
    }
}
