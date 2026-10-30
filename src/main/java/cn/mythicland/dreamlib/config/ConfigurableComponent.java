package cn.mythicland.dreamlib.config;

/**
 * Receives one immutable view of the owning plugin's configuration.
 *
 * <p>Lib invokes this method once during plugin startup and once before every plugin reload is
 * forwarded to ordinary lifecycle components. Implementations should build a complete snapshot
 * first and publish it only after validation succeeds.</p>
 */
public interface ConfigurableComponent {

    /**
     * Loads the current configuration snapshot.
     *
     * @param configuration configuration view owned by Lib
     */
    void reload(ConfigView configuration);
}
