package cn.mythicland.dreamlib.integration;

import cn.mythicland.dreamlib.plugin.PluginLookup;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Objects;
import java.util.Optional;
import java.util.logging.Level;

/**
 * Reflective PlaceholderAPI bridge.
 */
public final class PlaceholderApiBridge implements PlaceholderService {

    private final JavaPlugin owner;
    private volatile Method setPlaceholders;
    private volatile boolean lookupFailed;
    private volatile boolean failureLogged;

    /**
     * Creates a bridge and validates PlaceholderAPI's public method once.
     *
     * @param owner plugin receiving diagnostics
     */
    public PlaceholderApiBridge(JavaPlugin owner) {
        this.owner = Objects.requireNonNull(owner, "owner");
    }

    @Override
    public boolean isAvailable() {
        return ensureMethod() != null;
    }

    @Override
    public String render(Player player, String text) {
        Method renderer = ensureMethod();
        if (renderer == null) return text;
        try {
            Object result = renderer.invoke(null, player, text);
            if (!(result instanceof String rendered)) {
                throw new IllegalStateException("PlaceholderAPI returned a non-string result");
            }
            return rendered;
        } catch (IllegalAccessException | InvocationTargetException exception) {
            throw new IllegalStateException("PlaceholderAPI failed to render a template", exception);
        }
    }

    private synchronized Method ensureMethod() {
        if (setPlaceholders != null) return setPlaceholders;
        if (lookupFailed) return null;
        PluginLookup lookup = new PluginLookup(owner);
        Optional<Plugin> placeholderApi = lookup.find("PlaceholderAPI");
        if (placeholderApi.isEmpty()) return null;
        try {
            Class<?> placeholderType = Class.forName(
                    "me.clip.placeholderapi.PlaceholderAPI",
                    true,
                    placeholderApi.orElseThrow().getClass().getClassLoader()
            );
            Method method = placeholderType.getMethod("setPlaceholders", Player.class, String.class);
            owner.getLogger().info("PlaceholderAPI compatibility enabled.");
            setPlaceholders = method;
            return method;
        } catch (ClassNotFoundException | NoSuchMethodException exception) {
            lookupFailed = true;
            if (!failureLogged) {
                failureLogged = true;
                owner.getLogger().log(
                        Level.WARNING,
                        "PlaceholderAPI is enabled but its public placeholder method is unavailable.",
                        exception
                );
            }
            return null;
        }
    }
}
