package cn.mythicland.dreamlib.config;

import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Objects;
import java.util.function.Consumer;

/**
 * Shared helpers for loading and validating plugin configuration files.
 */
public final class ConfigSupport {

    private ConfigSupport() {
    }

    /**
     * Loads a plugin configuration and writes missing values from {@code config.yml} into the file.
     * Unknown keys already present in the file are preserved and are not migrated or removed.
     *
     * @param plugin the plugin that owns the configuration
     * @return the loaded configuration with default values attached and missing values materialized
     */
    public static FileConfiguration loadDefault(JavaPlugin plugin) {
        plugin.saveDefaultConfig();
        plugin.reloadConfig();
        FileConfiguration configuration = attachDefaults(plugin.getConfig());
        plugin.saveConfig();
        return configuration;
    }

    /**
     * Reloads a plugin configuration without writing the in-memory configuration back to disk.
     *
     * <p>This method is intended for explicit reload commands. It refreshes the configuration
     * from the file and attaches the bundled defaults in memory, but it never persists the
     * current snapshot. Callers must not use it for configuration migrations or other writes.</p>
     *
     * @param plugin the plugin that owns the configuration
     * @return the freshly loaded configuration with defaults attached
     */
    public static FileConfiguration reload(JavaPlugin plugin) {
        plugin.reloadConfig();
        return attachDefaults(plugin.getConfig());
    }

    /**
     * Loads the default configuration and exposes it through Lib's annotation binding view.
     *
     * @param plugin the plugin that owns the configuration
     * @return configuration binding view
     */
    public static ConfigView loadDefaultView(JavaPlugin plugin) {
        Objects.requireNonNull(plugin, "plugin");
        return new ConfigView(loadDefault(plugin), plugin.getLogger()::warning);
    }

    /**
     * Reloads the configuration and exposes it through Lib's annotation binding view without
     * writing the current snapshot back to disk.
     *
     * @param plugin the plugin that owns the configuration
     * @return configuration binding view
     */
    public static ConfigView reloadView(JavaPlugin plugin) {
        Objects.requireNonNull(plugin, "plugin");
        return new ConfigView(reload(plugin), plugin.getLogger()::warning);
    }

    /**
     * Binds an annotated record to an arbitrary Bukkit configuration source.
     *
     * <p>This is useful for plugin-owned files such as {@code scoreboard.yml}. The file loading
     * and persistence policy remains with the owning plugin; Lib only performs typed binding and
     * validation fallback.</p>
     *
     * @param configuration   configuration source
     * @param type            annotated record type
     * @param warningConsumer receives invalid-value warnings
     * @param <T>             bound record type
     * @return immutable bound configuration model
     */
    public static <T> T bind(
            FileConfiguration configuration,
            Class<T> type,
            Consumer<String> warningConsumer
    ) {
        return ConfigBinder.bind(
                Objects.requireNonNull(configuration, "configuration"),
                Objects.requireNonNull(warningConsumer, "warningConsumer"),
                Objects.requireNonNull(type, "type")
        );
    }

    /**
     * Binds an annotated record to an arbitrary Bukkit configuration source without warnings.
     *
     * @param configuration configuration source
     * @param type          annotated record type
     * @param <T>           bound record type
     * @return immutable bound configuration model
     */
    public static <T> T bind(FileConfiguration configuration, Class<T> type) {
        return bind(configuration, type, ignored -> {
        });
    }

    static FileConfiguration attachDefaults(FileConfiguration configuration) {
        Objects.requireNonNull(configuration, "configuration").options().copyDefaults(true);
        return configuration;
    }

    /**
     * Reads a required string and resets missing or invalid values to the supplied default.
     *
     * @param plugin        the plugin that owns the configuration and receives validation warnings
     * @param configuration the loaded plugin configuration
     * @param path          the configuration path
     * @param defaultValue  the value written when the configured value is absent or invalid
     * @return the trimmed configured value or the default value
     */
    public static String getString(
            JavaPlugin plugin,
            FileConfiguration configuration,
            String path,
            String defaultValue
    ) {
        Object rawValue = configuration.get(path);
        if (!configuration.contains(path)) {
            configuration.set(path, defaultValue);
            plugin.saveConfig();
            return defaultValue;
        }
        if (!(rawValue instanceof String value) || value.isBlank()) {
            return resetToDefault(plugin, configuration, path, defaultValue, "expected a non-empty string");
        }
        return value.trim();
    }

    /**
     * Reads a strict boolean and resets missing or invalid values to the supplied default.
     *
     * @param plugin        the plugin that owns the configuration and receives validation warnings
     * @param configuration the loaded plugin configuration
     * @param path          the configuration path
     * @param defaultValue  the value written when the configured value is absent or invalid
     * @return the configured boolean or the default value
     */
    public static boolean getBoolean(
            JavaPlugin plugin,
            FileConfiguration configuration,
            String path,
            boolean defaultValue
    ) {
        Object rawValue = configuration.get(path);
        if (!configuration.contains(path)) {
            configuration.set(path, defaultValue);
            plugin.saveConfig();
            return defaultValue;
        }
        if (!(rawValue instanceof Boolean value)) {
            return resetToDefault(plugin, configuration, path, defaultValue, "expected true or false");
        }
        return value;
    }

    /**
     * Replaces an invalid configuration value, logs a warning, and persists the default.
     *
     * @param plugin        the plugin that owns the configuration
     * @param configuration the loaded plugin configuration
     * @param path          the invalid configuration path
     * @param defaultValue  the replacement value
     * @param reason        a concise explanation of the validation failure
     * @param <T>           the replacement value type
     * @return the supplied default value
     */
    public static <T> T resetToDefault(
            JavaPlugin plugin,
            FileConfiguration configuration,
            String path,
            T defaultValue,
            String reason
    ) {
        plugin.getLogger().warning(
                "Invalid configuration '" + path + "': " + reason + "; resetting to the default value."
        );
        configuration.set(path, defaultValue);
        plugin.saveConfig();
        return defaultValue;
    }
}
