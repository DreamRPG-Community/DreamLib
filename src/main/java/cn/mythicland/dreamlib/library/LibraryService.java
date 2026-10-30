package cn.mythicland.dreamlib.library;

import org.bukkit.plugin.java.JavaPlugin;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.*;
import java.util.logging.Logger;

/**
 * Downloads, verifies, and loads immutable runtime library artifacts for dependent plugins.
 *
 * <p>The service deliberately mutates only the requesting plugin's own {@code libs} directory.
 * A dependency is never accepted before its configured SHA-256 digest has been verified.</p>
 */
public final class LibraryService {

    /**
     * Default Maven Central repository root.
     */
    public static final URI DEFAULT_REPOSITORY = URI.create("https://repo1.maven.org/maven2/");

    private static final int CONNECT_TIMEOUT_MILLIS = 30_000;
    private static final int READ_TIMEOUT_MILLIS = 60_000;
    private static final String LIBRARY_DIRECTORY_NAME = "libs";

    private static URI normalizeRepository(URI repository) {
        Objects.requireNonNull(repository, "repository");
        if (repository.getScheme() == null
                || (!repository.getScheme().equalsIgnoreCase("http")
                && !repository.getScheme().equalsIgnoreCase("https"))) {
            throw new IllegalArgumentException("Library repository must use HTTP or HTTPS: " + repository);
        }
        if (repository.getQuery() != null || repository.getFragment() != null) {
            throw new IllegalArgumentException("Library repository cannot contain query or fragment: " + repository);
        }
        String value = repository.toString();
        return URI.create(value.endsWith("/") ? value : value + "/");
    }

    private static URLClassLoader requireUrlClassLoader(JavaPlugin requester) {
        ClassLoader classLoader = requester.getClass().getClassLoader();
        if (!(classLoader instanceof URLClassLoader urlClassLoader)) {
            throw new IllegalStateException(
                    "Plugin classloader is not URLClassLoader: " + classLoader.getClass().getName()
            );
        }
        return urlClassLoader;
    }

    private static void validateUniqueFileNames(Collection<LibrarySpec> specifications) {
        Set<String> fileNames = new HashSet<>();
        for (LibrarySpec specification : specifications) {
            if (!fileNames.add(specification.fileName())) {
                throw new IllegalArgumentException(
                        "Duplicate library fileName: " + specification.fileName()
                );
            }
        }
    }

    private static Path resolveLibraryDirectory(JavaPlugin requester) {
        Path pluginDataDirectory = requester.getDataFolder().toPath().toAbsolutePath().normalize();
        Path libraryDirectory = pluginDataDirectory.resolve(LIBRARY_DIRECTORY_NAME).normalize();
        if (!libraryDirectory.startsWith(pluginDataDirectory)
                || libraryDirectory.equals(pluginDataDirectory)
                || Files.isSymbolicLink(libraryDirectory)) {
            throw new IllegalStateException("Invalid dependency directory: " + libraryDirectory);
        }
        return libraryDirectory;
    }

    private static void ensureLibraryDirectory(Path libraryDirectory) {
        try {
            Files.createDirectories(libraryDirectory);
        } catch (IOException exception) {
            throw new IllegalStateException(
                    "Failed to create dependency directory: " + libraryDirectory,
                    exception
            );
        }
        if (!Files.isDirectory(libraryDirectory) || Files.isSymbolicLink(libraryDirectory)) {
            throw new IllegalStateException("Dependency path is not a real directory: " + libraryDirectory);
        }
    }

    private static Path resolveJar(Path libraryDirectory, LibrarySpec specification) {
        Path jar = libraryDirectory.resolve(specification.fileName()).normalize();
        if (!jar.startsWith(libraryDirectory) || jar.equals(libraryDirectory)) {
            throw new IllegalArgumentException("Invalid dependency file path: " + specification.fileName());
        }
        if (Files.isSymbolicLink(jar)) {
            throw new IllegalStateException("Dependency file cannot be a symbolic link: " + jar);
        }
        return jar;
    }

    private static void resolveJarFile(
            Logger logger,
            LibrarySpec specification,
            URI repository,
            Path jar
    ) {
        if (Files.isRegularFile(jar)) {
            verifyDigest(logger, specification, jar);
            logger.info("Loading dependency jar: " + jar.getFileName());
            return;
        }
        downloadJar(logger, specification, repository, jar);
        logger.info("Loading dependency jar: " + jar.getFileName());
    }

    private static void verifyDigest(Logger logger, LibrarySpec specification, Path jar) {
        String actualDigest = sha256(jar);
        if (specification.sha256().equals(actualDigest)) return;

        logger.severe(
                "Dependency checksum mismatch: " + jar.getFileName()
                        + " expected=" + specification.sha256()
                        + " actual=" + actualDigest
        );
        throw new IllegalStateException(
                "Dependency checksum mismatch for " + specification.coordinate()
                        + ": expected " + specification.sha256() + ", actual " + actualDigest
        );
    }

    private static void downloadJar(
            Logger logger,
            LibrarySpec specification,
            URI repository,
            Path target
    ) {
        URI source = repository.resolve(specification.repositoryPath());
        Path temporary = target.resolveSibling(target.getFileName() + ".tmp");
        logger.info("Downloading dependency jar: " + target.getFileName());
        try {
            Files.deleteIfExists(temporary);
            URLConnection connection = openConnection(source);
            try (InputStream input = connection.getInputStream();
                 DigestInputStream digestInput = new DigestInputStream(
                         input,
                         messageDigest()
                 );
                 OutputStream output = Files.newOutputStream(
                         temporary,
                         StandardOpenOption.CREATE_NEW,
                         StandardOpenOption.WRITE
                 )) {
                digestInput.transferTo(output);
                String actualDigest = hexDigest(digestInput.getMessageDigest().digest());
                if (!specification.sha256().equals(actualDigest)) {
                    logger.severe(
                            "Dependency checksum mismatch: " + target.getFileName()
                                    + " expected=" + specification.sha256()
                                    + " actual=" + actualDigest
                    );
                    throw new IllegalStateException(
                            "Dependency checksum mismatch for " + specification.coordinate()
                                    + ": expected " + specification.sha256() + ", actual " + actualDigest
                    );
                }
            }
            Files.move(
                    temporary,
                    target,
                    StandardCopyOption.ATOMIC_MOVE,
                    StandardCopyOption.REPLACE_EXISTING
            );
        } catch (IOException exception) {
            deleteTemporary(temporary, exception);
            logger.severe(
                    "Failed to download dependency jar: " + specification.coordinate()
                            + " from " + source
            );
            throw new IllegalStateException(
                    "Failed to download dependency jar: " + specification.coordinate()
                            + " from " + source,
                    exception
            );
        } catch (IllegalStateException exception) {
            deleteTemporary(temporary, exception);
            throw exception;
        }
    }

    private static URLConnection openConnection(URI source) throws IOException {
        URLConnection connection = source.toURL().openConnection();
        connection.setConnectTimeout(CONNECT_TIMEOUT_MILLIS);
        connection.setReadTimeout(READ_TIMEOUT_MILLIS);
        connection.setRequestProperty("User-Agent", "DreamRPG-LibraryLoader");
        if (connection instanceof HttpURLConnection httpConnection
                && httpConnection.getResponseCode() >= HttpURLConnection.HTTP_BAD_REQUEST) {
            throw new IOException(
                    "HTTP " + httpConnection.getResponseCode() + " while requesting " + source
            );
        }
        return connection;
    }

    private static void deleteTemporary(Path temporary, Throwable failure) {
        try {
            Files.deleteIfExists(temporary);
        } catch (IOException cleanupFailure) {
            failure.addSuppressed(cleanupFailure);
        }
    }

    static String sha256(Path file) {
        try (InputStream input = Files.newInputStream(file);
             DigestInputStream digestInput = new DigestInputStream(input, messageDigest())) {
            digestInput.transferTo(OutputStream.nullOutputStream());
            return hexDigest(digestInput.getMessageDigest().digest());
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to hash dependency file: " + file, exception);
        }
    }

    private static MessageDigest messageDigest() {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("JDK SHA-256 implementation is unavailable", exception);
        }
    }

    private static String hexDigest(byte[] digest) {
        StringBuilder result = new StringBuilder(digest.length * 2);
        for (byte value : digest) {
            result.append(String.format("%02x", value));
        }
        return result.toString();
    }

    private static ClassLoader createDependencyClassLoader(
            URLClassLoader pluginClassLoader,
            Collection<Path> jars
    ) {
        List<URL> urls = new ArrayList<>();
        for (Path jar : jars) {
            try {
                urls.add(jar.toUri().toURL());
            } catch (IOException exception) {
                throw new IllegalStateException("Failed to resolve dependency URL: " + jar, exception);
            }
        }

        /*
         * Paper 1.12.2 inherits URLClassLoader.addURL(URL) without exposing a plugin-level bridge.
         * On JDK 21, opening java.net reflectively is rejected by JPMS. A child loader keeps the
         * verified JARs isolated while retaining the requesting Paper loader as their parent.
         */
        return new URLClassLoader(
                urls.toArray(URL[]::new),
                pluginClassLoader
        );
    }

    /**
     * Loads the requested libraries into a class loader owned by the requesting plugin.
     *
     * <p>This method is intentionally synchronous and should be called during plugin startup,
     * before classes that use the external library are initialized. A verified cache is used
     * without network access. Download or class-loader failures are fatal to the caller.</p>
     *
     * <p>The download path is blocking. Callers must not invoke it from a request or gameplay
     * callback that cannot tolerate network latency.</p>
     *
     * @param requester      plugin that owns the cache directory and dependency class loader
     * @param specifications immutable library specifications
     * @param repository     one repository root used to resolve every specification
     * @return verified paths and a dependency class loader whose parent is the plugin loader
     * @throws IllegalStateException if a library cannot be verified, downloaded, or loaded
     */
    public LibraryLoadResult load(
            JavaPlugin requester,
            Collection<LibrarySpec> specifications,
            URI repository
    ) {
        Objects.requireNonNull(requester, "requester");
        Objects.requireNonNull(specifications, "specifications");
        URI normalizedRepository = normalizeRepository(repository);
        URLClassLoader pluginClassLoader = requireUrlClassLoader(requester);
        List<LibrarySpec> requestedLibraries = List.copyOf(specifications);
        validateUniqueFileNames(requestedLibraries);

        Path libraryDirectory = resolveLibraryDirectory(requester);
        ensureLibraryDirectory(libraryDirectory);
        List<Path> resolvedJars = new ArrayList<>();
        for (LibrarySpec specification : requestedLibraries) {
            Path jar = resolveJar(libraryDirectory, specification);
            resolveJarFile(requester.getLogger(), specification, normalizedRepository, jar);
            resolvedJars.add(jar);
        }

        ClassLoader dependencyClassLoader = createDependencyClassLoader(pluginClassLoader, resolvedJars);
        return new LibraryLoadResult(resolvedJars, dependencyClassLoader);
    }
}
