package cn.mythicland.dreamlib;

import cn.mythicland.dreamlib.api.DreamLibApi;
import org.bukkit.plugin.ServicePriority;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Bukkit entry point that owns and publishes the shared {@link DreamLibApi} service.
 */
public final class DreamLibPlugin extends JavaPlugin {

    private DreamLibApi api;

    @Override
    public void onEnable() {
        this.api = new DreamLibApi(this, DreamLibApi.defaultPoolSize());
        getServer().getServicesManager().register(
                DreamLibApi.class,
                api,
                this,
                ServicePriority.Normal
        );

        getLogger().info("Lib runtime initialized.");
    }

    @Override
    public void onDisable() {
        if (api == null) return;

        getServer().getServicesManager().unregister(DreamLibApi.class, api);
        api.close();
        api = null;
    }

    /**
     * Returns the service owned by this plugin.
     *
     * @return the active Lib service, or {@code null} after the plugin has been disabled
     */
    public DreamLibApi getApi() {
        return api;
    }
}
