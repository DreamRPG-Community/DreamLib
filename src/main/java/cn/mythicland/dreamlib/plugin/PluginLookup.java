package cn.mythicland.dreamlib.plugin;

import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.PluginManager;

import java.util.Objects;
import java.util.Optional;

/**
 * Type-safe lookup for optional Bukkit plugins.
 */
public final class PluginLookup {

    private final PluginManager pluginManager;

    public PluginLookup(Plugin owner) {
        Objects.requireNonNull(owner, "owner");
        this.pluginManager = owner.getServer().getPluginManager();
    }

    /**
     * Finds an enabled plugin by its Bukkit name.
     */
    public Optional<Plugin> find(String pluginName) {
        Objects.requireNonNull(pluginName, "pluginName");
        Plugin plugin = pluginManager.getPlugin(pluginName);
        return plugin == null || !plugin.isEnabled() ? Optional.empty() : Optional.of(plugin);
    }

    /**
     * Finds an enabled plugin and verifies its implementation type.
     */
    public <T extends Plugin> Optional<T> find(String pluginName, Class<T> pluginType) {
        Objects.requireNonNull(pluginType, "pluginType");
        return find(pluginName).filter(pluginType::isInstance).map(pluginType::cast);
    }
}
