package cn.mythicland.dreamlib.config;

import org.bukkit.configuration.file.FileConfiguration;

import java.util.Objects;
import java.util.function.Consumer;

/**
 * Read-only binding boundary for one configuration load operation.
 */
public final class ConfigView {

    private final FileConfiguration configuration;
    private final Consumer<String> warningConsumer;

    ConfigView(FileConfiguration configuration, Consumer<String> warningConsumer) {
        this.configuration = Objects.requireNonNull(configuration, "configuration");
        this.warningConsumer = Objects.requireNonNull(warningConsumer, "warningConsumer");
    }

    /**
     * Binds an annotated record to the current configuration.
     *
     * @param type annotated record type
     * @param <T>  record type
     * @return immutable bound configuration model
     */
    public <T> T bind(Class<T> type) {
        return ConfigBinder.bind(configuration, warningConsumer, type);
    }
}
