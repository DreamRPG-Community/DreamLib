package cn.mythicland.dreamlib.storage;

import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;

class AtomicFileStoreTest {

    @Test
    void replacesContentAndLeavesNoTemporaryFiles(@TempDir Path directory) throws Exception {
        Path target = directory.resolve("nested").resolve("data.yml");
        AtomicFileStore.writeUtf8(target, "first");
        AtomicFileStore.writeUtf8(target, "second");

        assertEquals("second", Files.readString(target));
        try (var files = Files.list(target.getParent())) {
            assertEquals(1, files.count());
        }
    }

    @Test
    void restoresCapturedBytes(@TempDir Path directory) throws Exception {
        Path target = directory.resolve("data.yml");
        AtomicFileStore.writeUtf8(target, "before");
        AtomicFileStore.FileSnapshot snapshot = AtomicFileStore.snapshot(target);
        AtomicFileStore.writeUtf8(target, "after");

        AtomicFileStore.restore(target, snapshot);

        assertEquals("before", Files.readString(target));
    }

    @Test
    void writesYamlTreesAtomically(@TempDir Path directory) throws Exception {
        Path target = directory.resolve("items.yml");

        AtomicYamlStore.write(target, java.util.Map.of(
                "STONE", java.util.Map.of("Id", "STONE", "Attributes", java.util.List.of("generic.attackDamage"))
        ));

        YamlConfiguration configuration = YamlConfiguration.loadConfiguration(target.toFile());
        assertEquals("STONE", configuration.getString("STONE.Id"));
        assertEquals("generic.attackDamage", configuration.getStringList("STONE.Attributes").getFirst());
    }
}
