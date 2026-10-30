package cn.mythicland.dreamlib.material;

/**
 * Immutable localized Bukkit enchantment metadata.
 */
public record EnchantmentEntry(String key, String displayName, int startLevel, int maxLevel) {
    public EnchantmentEntry {
        if (key == null || key.isBlank()) throw new IllegalArgumentException("key is required");
        displayName = displayName == null || displayName.isBlank() ? key : displayName;
        if (startLevel < 1) startLevel = 1;
        if (maxLevel < startLevel) maxLevel = startLevel;
    }
}
