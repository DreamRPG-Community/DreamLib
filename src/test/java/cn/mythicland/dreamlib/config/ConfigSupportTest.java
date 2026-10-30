package cn.mythicland.dreamlib.config;

import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

class ConfigSupportTest {

    @Test
    void attachingDefaultsKeepsEditedValuesAndExposesMissingDefaultsInMemory() {
        YamlConfiguration defaults = new YamlConfiguration();
        defaults.set("value", "default");
        defaults.set("missing", "bundled-default");
        YamlConfiguration editedConfiguration = new YamlConfiguration();
        editedConfiguration.setDefaults(defaults);
        editedConfiguration.set("value", "edited");

        FileConfiguration reloaded = ConfigSupport.attachDefaults(editedConfiguration);

        assertSame(editedConfiguration, reloaded);
        assertEquals("edited", reloaded.getString("value"));
        assertEquals("bundled-default", reloaded.getString("missing"));
    }
}
