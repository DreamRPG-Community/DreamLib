package cn.mythicland.dreamlib;

import cn.mythicland.dreamlib.api.DreamLibApi;
import cn.mythicland.framework.bukkit.bootstrap.ManagedPlugin;
import org.bukkit.plugin.ServicePriority;

/**
 * Bukkit entry point that owns and publishes the shared {@link DreamLibApi} service.
 */
public final class DreamLibPlugin extends ManagedPlugin {

    private DreamLibApi api;

    @Override
    protected void enablePlugin() {
        this.api = new DreamLibApi(this, DreamLibApi.defaultPoolSize());
        getServer().getServicesManager().register(
                DreamLibApi.class,
                api,
                this,
                ServicePriority.Normal
        );

        getLogger().info("DreamLib runtime initialized.");
    }

    @Override
    protected void disablePlugin() {
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
