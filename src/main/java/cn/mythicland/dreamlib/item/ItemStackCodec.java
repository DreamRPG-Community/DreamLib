package cn.mythicland.dreamlib.item;

import org.bukkit.inventory.ItemStack;
import org.bukkit.configuration.serialization.ConfigurationSerialization;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Serializes Bukkit item stacks through Bukkit's configuration serialization contract.
 */
public final class ItemStackCodec {

    private ItemStackCodec() {
    }

    /**
     * Serializes an item stack into a detached map.
     *
     * @param itemStack the item stack to serialize
     * @return a mutable map containing the serialized item stack
     * @throws NullPointerException if {@code itemStack} is null
     */
    public static Map<String, Object> serialize(ItemStack itemStack) {
        Objects.requireNonNull(itemStack, "itemStack");
        return new LinkedHashMap<>(itemStack.serialize());
    }

    /**
     * Deserializes an item stack from a Bukkit configuration map.
     *
     * @param values the serialized item values
     * @return the decoded item stack
     * @throws NullPointerException     if {@code values} is null
     * @throws IllegalArgumentException if Bukkit cannot decode the values
     */
    public static ItemStack deserialize(Map<String, Object> values) {
        Objects.requireNonNull(values, "values");
        Map<String, Object> decoded = new LinkedHashMap<>(values);
        if (decoded.get("meta") instanceof Map<?, ?> metadata) {
            Map<String, Object> serializedMeta = new LinkedHashMap<>();
            metadata.forEach((key, value) -> serializedMeta.put(String.valueOf(key), deserializeValue(value)));
            decoded.put("meta", ConfigurationSerialization.deserializeObject(serializedMeta));
        }
        return ItemStack.deserialize(decoded);
    }

    private static Object deserializeValue(Object value) {
        if (value instanceof Map<?, ?> map) {
            Map<String, Object> decoded = new LinkedHashMap<>();
            map.forEach((key, child) -> decoded.put(String.valueOf(key), deserializeValue(child)));
            if (decoded.containsKey(ConfigurationSerialization.SERIALIZED_TYPE_KEY)) {
                return ConfigurationSerialization.deserializeObject(decoded);
            }
            return decoded;
        }
        if (value instanceof Iterable<?> sequence) {
            java.util.List<Object> decoded = new java.util.ArrayList<>();
            sequence.forEach(child -> decoded.add(deserializeValue(child)));
            return decoded;
        }
        return value;
    }
}
