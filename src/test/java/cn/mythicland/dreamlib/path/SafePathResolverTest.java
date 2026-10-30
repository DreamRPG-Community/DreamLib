package cn.mythicland.dreamlib.path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class SafePathResolverTest {

    @TempDir
    Path temporaryDirectory;

    @Test
    void resolvesSingleDirectorySegmentsInsideRoot() throws Exception {
        Path root = temporaryDirectory.resolve("worlds");
        SafePathResolver resolver = new SafePathResolver(root);

        resolver.ensureRootDirectory();

        assertEquals("城镇", resolver.normalizeSingleSegment("城镇"));
        assertEquals(root.resolve("城镇"), resolver.resolveSingleSegment("城镇"));
        assertEquals(root, resolver.root());
        Files.createDirectory(resolver.resolveSingleSegment("城镇"));
    }

    @Test
    void rejectsTraversalAndNestedPaths() {
        SafePathResolver resolver = new SafePathResolver(temporaryDirectory.resolve("worlds"));

        assertThrows(IllegalArgumentException.class, () -> resolver.normalizeSingleSegment("../outside"));
        assertThrows(IllegalArgumentException.class, () -> resolver.normalizeSingleSegment("nested/world"));
        assertThrows(IllegalArgumentException.class, () -> resolver.normalizeSingleSegment(".."));
        assertThrows(IllegalArgumentException.class, () -> resolver.normalizeSingleSegment(" world"));
        assertThrows(IllegalArgumentException.class, () -> resolver.resolveRelative("../outside.yml"));
        assertThrows(IllegalArgumentException.class, () -> resolver.resolveRelative("/absolute.yml"));
    }

    @Test
    void rejectsSymbolicLinkRoot() throws Exception {
        Path realRoot = temporaryDirectory.resolve("real");
        Path linkedRoot = temporaryDirectory.resolve("linked");
        Files.createDirectory(realRoot);

        try {
            Files.createSymbolicLink(linkedRoot, realRoot);
        } catch (UnsupportedOperationException | SecurityException | IOException exception) {
            return;
        }

        assertThrows(IOException.class, () -> new SafePathResolver(linkedRoot).ensureRootDirectory());
    }
}
