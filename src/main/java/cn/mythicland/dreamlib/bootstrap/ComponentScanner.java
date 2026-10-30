package cn.mythicland.dreamlib.bootstrap;

import java.io.IOException;
import java.net.JarURLConnection;
import java.net.URISyntaxException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.stream.Stream;

/**
 * JDK-only scanner for classes in one plugin package.
 */
final class ComponentScanner {

    private ComponentScanner() {
    }

    static List<Class<?>> scan(ClassLoader classLoader, String basePackage) {
        String packagePath = basePackage.replace('.', '/');
        Set<String> classNames = new HashSet<>();
        try {
            Enumeration<URL> resources = classLoader.getResources(packagePath);
            while (resources.hasMoreElements()) collectResource(resources.nextElement(), packagePath, classNames);
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to scan Lib component package: " + basePackage, exception);
        }
        if (classLoader instanceof URLClassLoader urlClassLoader) {
            for (URL url : urlClassLoader.getURLs()) collectClassLoaderUrl(url, packagePath, classNames);
        }

        List<Class<?>> result = new ArrayList<>();
        List<String> orderedNames = classNames.stream().sorted().toList();
        for (String className : orderedNames) {
            Class<?> type = loadScannedClass(classLoader, className);
            if (type != null) result.add(type);
        }
        return List.copyOf(result);
    }

    static Class<?> loadScannedClass(ClassLoader classLoader, String className) {
        try {
            return Class.forName(className, false, classLoader);
        } catch (ClassNotFoundException exception) {
            throw new IllegalStateException("Cannot load scanned Lib component: " + className, exception);
        } catch (NoClassDefFoundError ignored) {
            // Optional integrations may be compiled into the plugin but not installed at runtime.
            return null;
        }
    }

    private static void collectResource(URL resource, String packagePath, Set<String> classNames) {
        try {
            if (resource.getProtocol().equals("file")) {
                collectDirectory(Path.of(resource.toURI()), packagePath, classNames);
                return;
            }
            if (resource.getProtocol().equals("jar")) {
                JarURLConnection connection = (JarURLConnection) resource.openConnection();
                connection.setUseCaches(false);
                try (JarFile jarFile = connection.getJarFile()) {
                    collectJar(jarFile, packagePath, classNames);
                }
                return;
            }
            throw new IllegalStateException("Unsupported component scan resource protocol: " + resource);
        } catch (IOException | URISyntaxException exception) {
            throw new IllegalStateException("Failed to scan Lib component resource: " + resource, exception);
        }
    }

    private static void collectClassLoaderUrl(URL url, String packagePath, Set<String> classNames) {
        try {
            if (!url.getProtocol().equals("file")) return;
            Path path = Path.of(url.toURI());
            if (Files.isDirectory(path)) {
                collectDirectory(path.resolve(packagePath), packagePath, classNames);
                return;
            }
            if (path.toString().endsWith(".jar")) {
                try (JarFile jarFile = new JarFile(path.toFile())) {
                    collectJar(jarFile, packagePath, classNames);
                }
            }
        } catch (IOException | URISyntaxException exception) {
            throw new IllegalStateException("Failed to scan plugin classloader URL: " + url, exception);
        }
    }

    private static void collectDirectory(Path packageDirectory, String packagePath, Set<String> classNames)
            throws IOException {
        if (!Files.isDirectory(packageDirectory)) return;
        try (Stream<Path> paths = Files.walk(packageDirectory)) {
            for (Path classFile : paths
                    .filter(Files::isRegularFile)
                    .filter(path -> path.toString().endsWith(".class"))
                    .sorted(Comparator.comparing(Path::toString))
                    .toList()) {
                String absolute = classFile.toString().replace('\\', '/');
                int packageIndex = absolute.indexOf(packagePath);
                if (packageIndex < 0) continue;
                String className = absolute.substring(packageIndex, absolute.length() - ".class".length())
                        .replace('/', '.');
                addClassName(className, classNames);
            }
        }
    }

    private static void collectJar(JarFile jarFile, String packagePath, Set<String> classNames) {
        Enumeration<JarEntry> entries = jarFile.entries();
        while (entries.hasMoreElements()) {
            String name = entries.nextElement().getName();
            if (!name.startsWith(packagePath + "/") || !name.endsWith(".class")) continue;
            addClassName(name.substring(0, name.length() - ".class".length()).replace('/', '.'), classNames);
        }
    }

    private static void addClassName(String className, Set<String> classNames) {
        if (className.endsWith("module-info") || className.endsWith("package-info")) return;
        classNames.add(className);
    }
}
