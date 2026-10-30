package cn.mythicland.dreamlib.item;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Immutable, plugin-neutral identity marker stored in an item stack's hidden NBT.
 *
 * @param namespace root NBT compound name owned by the marker producer
 * @param schema    marker schema version
 * @param values    string fields stored below the namespace
 */
public record ItemMarker(
        String namespace,
        int schema,
        Map<String, String> values
) {

    /**
     * Validates and detaches one marker.
     */
    public ItemMarker {
        namespace = requireText(namespace, "namespace");
        if (schema < 1) throw new IllegalArgumentException("schema must be positive");
        Objects.requireNonNull(values, "values");
        Map<String, String> copy = new LinkedHashMap<>();
        values.forEach((key, value) -> {
            String normalizedKey = requireText(key, "marker key");
            if ("schema".equals(normalizedKey)) {
                throw new IllegalArgumentException("marker values cannot contain reserved key schema");
            }
            if (copy.put(normalizedKey, Objects.requireNonNull(value, "marker value")) != null) {
                throw new IllegalArgumentException("duplicate marker key: " + normalizedKey);
            }
        });
        values = Map.copyOf(copy);
    }

    private static String requireText(String value, String fieldName) {
        String text = Objects.requireNonNull(value, fieldName).trim();
        if (text.isBlank()) throw new IllegalArgumentException(fieldName + " cannot be blank");
        return text;
    }
}
