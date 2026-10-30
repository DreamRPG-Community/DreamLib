package cn.mythicland.dreamlib.config;

import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ConfigBinderTest {

    @Test
    void bindsAnnotatedRecordComponentsAndKeepsTheModelImmutable() {
        YamlConfiguration configuration = new YamlConfiguration();
        configuration.set("distance", 4.5D);
        configuration.set("enabled", true);
        configuration.set("mode", "FAST");
        configuration.set("label", "  Shop  ");
        List<String> warnings = new ArrayList<>();

        BoundSettings settings = new ConfigView(configuration, warnings::add).bind(BoundSettings.class);

        assertEquals(4.5D, settings.distance());
        assertTrue(settings.enabled());
        assertEquals(Mode.FAST, settings.mode());
        assertEquals("Shop", settings.label());
        assertTrue(warnings.isEmpty());
    }

    @Test
    void invalidValuesUseDeclaredDefaultsAndProduceWarnings() {
        YamlConfiguration configuration = new YamlConfiguration();
        configuration.set("distance", -1.0D);
        configuration.set("enabled", "not-a-boolean");
        configuration.set("mode", "UNKNOWN");
        configuration.set("label", " ");
        List<String> warnings = new ArrayList<>();

        BoundSettings settings = new ConfigView(configuration, warnings::add).bind(BoundSettings.class);

        assertEquals(2.0D, settings.distance());
        assertFalse(settings.enabled());
        assertEquals(Mode.SAFE, settings.mode());
        assertEquals("Default", settings.label());
        assertEquals(4, warnings.size());
    }

    @Test
    void missingValuesUseDeclaredDefaultsWithoutWarnings() {
        List<String> warnings = new ArrayList<>();

        BoundSettings settings = new ConfigView(
                new YamlConfiguration(),
                warnings::add
        ).bind(BoundSettings.class);

        assertEquals(2.0D, settings.distance());
        assertFalse(settings.enabled());
        assertEquals(Mode.SAFE, settings.mode());
        assertEquals("Default", settings.label());
        assertTrue(warnings.isEmpty());
    }

    @Test
    void bindsAndValidatesAnnotatedNumericLists() {
        YamlConfiguration configuration = new YamlConfiguration();
        configuration.set("spacings", List.of(0.0D, 0.4D, 0.6D));
        List<String> warnings = new ArrayList<>();

        SpacingSettings settings = new ConfigView(configuration, warnings::add).bind(SpacingSettings.class);

        assertEquals(List.of(0.0D, 0.4D, 0.6D), settings.spacings());
        assertTrue(warnings.isEmpty());
    }

    @Test
    void invalidAnnotatedNumericListsUseTheDeclaredDefault() {
        YamlConfiguration configuration = new YamlConfiguration();
        configuration.set("spacings", List.of(0.0D, -0.4D));
        List<String> warnings = new ArrayList<>();

        SpacingSettings settings = new ConfigView(configuration, warnings::add).bind(SpacingSettings.class);

        assertEquals(List.of(0.0D, 0.25D), settings.spacings());
        assertEquals(1, warnings.size());
    }

    @Test
    void preservesWhitespaceWhenASecretOrTemplateDisablesTrimming() {
        YamlConfiguration configuration = new YamlConfiguration();
        configuration.set("value", "  keep both sides  ");

        UntrimmedSettings settings = ConfigSupport.bind(configuration, UntrimmedSettings.class);

        assertEquals("  keep both sides  ", settings.value());
    }

    private enum Mode {
        SAFE,
        FAST
    }

    private record BoundSettings(
            @ConfigValue(
                    path = "distance",
                    defaultValue = "2.0",
                    positive = true
            ) double distance,
            @ConfigValue(path = "enabled", defaultValue = "false") boolean enabled,
            @ConfigValue(path = "mode", defaultValue = "SAFE") Mode mode,
            @ConfigValue(
                    path = "label",
                    defaultValue = "Default",
                    nonBlank = true
            ) String label
    ) {
    }

    private record SpacingSettings(
            @ConfigValue(
                    path = "spacings",
                    defaultValue = "0.0,0.25",
                    nonNegative = true
            )
            List<Double> spacings
    ) {
    }

    private record UntrimmedSettings(
            @ConfigValue(
                    path = "value",
                    defaultValue = "  default  ",
                    trim = false
            )
            String value
    ) {
    }
}
