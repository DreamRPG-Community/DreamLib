package cn.mythicland.dreamlib.text;

import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.*;

class YamlMessageBundleTest {

    @Test
    void rendersLiteralPlaceholdersAndReportsVersion() throws Exception {
        Path file = Files.createTempFile("messages", ".yml");
        YamlConfiguration configuration = new YamlConfiguration();
        configuration.set("FileVersion", 771);
        configuration.set("Greeting", "&aHello {value1} [$1]");
        configuration.save(file.toFile());

        YamlMessageBundle bundle = YamlMessageBundle.load(file, 771, Logger.getLogger("test"));

        assertTrue(bundle.compatible());
        assertEquals(771, bundle.fileVersion());
        assertEquals("§aHello [name] [$1]", bundle.render("Greeting", Map.of("value1", "[name]"), "fallback"));
    }

    @Test
    void usesFallbackForStaleFiles() throws Exception {
        Path file = Files.createTempFile("messages", ".yml");
        YamlConfiguration configuration = new YamlConfiguration();
        configuration.set("FileVersion", 1);
        configuration.set("Greeting", "old");
        configuration.save(file.toFile());

        YamlMessageBundle bundle = YamlMessageBundle.load(file, 2, Logger.getLogger("test"));

        assertFalse(bundle.compatible());
        assertEquals("fallback", bundle.render("Greeting", Map.of(), "fallback"));
    }
}
