package cn.mythicland.dreamlib.material;

import java.util.Locale;
import java.util.Objects;

/**
 * A localized legacy Bukkit material entry.
 *
 * @param materialName the Bukkit material enum name
 * @param legacyId     the legacy numeric material id
 * @param data         the legacy durability or data value
 * @param displayName  the localized display name
 */
public record MaterialEntry(String materialName, int legacyId, int data, String displayName) {

    /**
     * Creates a validated material entry.
     */
    public MaterialEntry {
        materialName = Objects.requireNonNull(materialName, "materialName").trim().toUpperCase(Locale.ROOT);
        displayName = Objects.requireNonNull(displayName, "displayName").trim();
        if (materialName.isEmpty()) throw new IllegalArgumentException("materialName must not be blank");
        if (displayName.isEmpty()) throw new IllegalArgumentException("displayName must not be blank");
        if (legacyId < 0) throw new IllegalArgumentException("legacyId must not be negative");
        if (data < 0 || data > 32767) throw new IllegalArgumentException("data must be between 0 and 32767");
    }

    /**
     * Returns the stable material and data lookup key.
     *
     * @return a key such as {@code INK_SACK.4}
     */
    public String key() {
        return materialName + "." + data;
    }
}
