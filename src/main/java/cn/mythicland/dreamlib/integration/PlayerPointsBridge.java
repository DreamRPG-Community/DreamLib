package cn.mythicland.dreamlib.integration;

import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.UUID;
import java.util.logging.Level;

/**
 * Reflective PlayerPoints API bridge used by plugins that must not bundle PlayerPoints.
 */
public final class PlayerPointsBridge implements PlayerPointsService {

    private static final String PLUGIN_NAME = "PlayerPoints";
    private static final String API_CLASS_NAME = "org.black_ixx.playerpoints.PlayerPointsAPI";

    private final JavaPlugin owner;
    private Object api;
    private Method lookFormatted;
    private boolean lookupFailed;
    private boolean failureLogged;

    /**
     * Creates a lazy PlayerPoints bridge.
     *
     * @param owner plugin receiving diagnostics
     */
    public PlayerPointsBridge(JavaPlugin owner) {
        this.owner = owner;
    }

    @Override
    public boolean isAvailable() {
        return ensureApi();
    }

    @Override
    public String formattedPoints(Player player) {
        if (!ensureApi()) throw new IllegalStateException("PlayerPoints API is unavailable");
        try {
            Object result = lookFormatted.invoke(api, player.getUniqueId());
            if (!(result instanceof String points)) {
                throw new IllegalStateException("PlayerPoints returned a non-string formatted balance");
            }
            return points;
        } catch (IllegalAccessException | InvocationTargetException exception) {
            throw new IllegalStateException("PlayerPoints lookup failed", exception);
        }
    }

    private boolean ensureApi() {
        if (api != null && lookFormatted != null) return true;
        if (lookupFailed) return false;
        Plugin plugin = owner.getServer().getPluginManager().getPlugin(PLUGIN_NAME);
        if (plugin == null || !plugin.isEnabled()) return false;
        try {
            Method getApi = plugin.getClass().getMethod("getAPI");
            Object service = getApi.invoke(plugin);
            if (service == null) throw new IllegalStateException("PlayerPoints getAPI returned null");
            api = service;
            Class<?> apiType = Class.forName(
                    API_CLASS_NAME,
                    true,
                    plugin.getClass().getClassLoader()
            );
            lookFormatted = apiType.getMethod("lookFormatted", UUID.class);
            return true;
        } catch (ReflectiveOperationException | RuntimeException exception) {
            lookupFailed = true;
            if (!failureLogged) {
                failureLogged = true;
                owner.getLogger().log(
                        Level.WARNING,
                        "PlayerPoints is enabled but its public API could not be used.",
                        exception
                );
            }
            return false;
        }
    }
}
