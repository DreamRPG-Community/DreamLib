package cn.mythicland.dreamlib.bootstrap;

import cn.mythicland.dreamlib.api.DreamLibApi;
import cn.mythicland.framework.bukkit.bootstrap.ManagedPlugin;
import cn.mythicland.framework.bukkit.bootstrap.PluginBootstrap;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;

/**
 * Bukkit entry point for plugins composed of ordered DreamLib component graphs.
 */
public abstract class DreamLibManagedPlugin extends ManagedPlugin {
    private final List<String> packages;
    private final Map<String, PluginBootstrap> bootstraps = new LinkedHashMap<>();

    /**
     * Defines the component packages in startup order.
     *
     * @param packages component packages to scan
     */
    protected DreamLibManagedPlugin(String... packages) {
        this.packages = List.of(packages);
        if (this.packages.isEmpty() || this.packages.stream().distinct().count() != this.packages.size()) {
            throw new IllegalArgumentException("Component packages must be nonempty and distinct");
        }
    }

    /**
     * Starts component graphs on the Bukkit primary thread.
     */
    @Override
    @SuppressWarnings("resource")
    protected final void enablePlugin() {
        DreamLibApi lib = DreamLibApi.require(this);
        for (String componentPackage : packages) {
            PluginBootstrap bootstrap = lib.createPluginBootstrap(this, componentPackage);
            bootstraps.put(componentPackage, bootstrap);
            bootstrap.enable();
        }
    }

    @Override
    protected final void onEnableFailure(RuntimeException exception) {
        getLogger().log(Level.SEVERE, getName() + " failed to enable: " + DreamLibApi.rootCauseMessage(exception),
                exception);
    }

    /**
     * Closes component graphs in reverse startup order on the Bukkit primary thread.
     */
    @Override
    protected final void disablePlugin() {
        for (String componentPackage : packages.reversed()) {
            PluginBootstrap bootstrap = bootstraps.get(componentPackage);
            if (bootstrap != null) {
                bootstrap.disable();
                bootstraps.remove(componentPackage);
            }
        }
    }

    /**
     * Returns a running component graph.
     *
     * @param componentPackage package supplied to the constructor
     * @return the component graph
     * @throws IllegalStateException if the graph is unavailable
     */
    protected final PluginBootstrap bootstrap(String componentPackage) {
        PluginBootstrap bootstrap = bootstraps.get(componentPackage);
        if (bootstrap == null)
            throw new IllegalStateException(getName() + " bootstrap is unavailable: " + componentPackage);
        return bootstrap;
    }
}
