package cn.mythicland.dreamlib.bootstrap;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Verifies the JDK-only scanner can discover compiled Lib component infrastructure.
 */
class ComponentScannerTest {

    @Test
    void scannerFindsClassesFromAnExplodedGradleClasspath() {
        List<Class<?>> classes = ComponentScanner.scan(
                getClass().getClassLoader(),
                "cn.mythicland.dreamlib.bootstrap"
        );

        assertTrue(classes.contains(ComponentContainer.class));
        assertTrue(classes.contains(PluginBootstrap.class));
    }

    @Test
    void scannerSkipsClassesWhoseOptionalDependencyIsAbsent() {
        ClassLoader classLoader = new ClassLoader(null) {
            @Override
            protected Class<?> loadClass(String name, boolean resolve) {
                throw new NoClassDefFoundError("optional/dependency/Type");
            }
        };

        assertNull(ComponentScanner.loadScannedClass(classLoader, "optional.Component"));
    }
}
