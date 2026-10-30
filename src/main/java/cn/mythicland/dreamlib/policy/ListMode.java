package cn.mythicland.dreamlib.policy;

import java.util.Locale;
import java.util.Optional;

/**
 * Matching modes for reusable allow and deny lists.
 */
public enum ListMode {

    /**
     * Disables matching and allows every value.
     */
    DISABLED("disabled"),

    /**
     * Blocks values that are present in the list.
     */
    BLACKLIST("blacklist"),

    /**
     * Blocks values that are absent from the list.
     */
    WHITELIST("whitelist");

    private final String configValue;

    ListMode(String configValue) {
        this.configValue = configValue;
    }

    /**
     * Parses a configuration value without throwing for invalid input.
     *
     * @param value the configured mode value
     * @return the matching mode, or empty when the value is null, blank, or unknown
     */
    public static Optional<ListMode> parse(String value) {
        if (value == null || value.isBlank()) return Optional.empty();
        String normalized = value.trim().toLowerCase(Locale.ROOT);
        for (ListMode mode : values()) {
            if (mode.configValue.equals(normalized)) return Optional.of(mode);
        }
        return Optional.empty();
    }

    /**
     * Returns the stable configuration value for this mode.
     *
     * @return the lower-case configuration value
     */
    public String configValue() {
        return configValue;
    }
}
