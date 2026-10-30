package cn.mythicland.dreamlib.storage;

import org.bukkit.configuration.serialization.ConfigurationSerializable;
import org.bukkit.configuration.serialization.ConfigurationSerialization;

import java.lang.reflect.Array;
import java.util.*;

/**
 * Defensive-copy helpers for YAML-compatible configuration trees.
 */
public final class YamlTree {

    private YamlTree() {
    }

    /**
     * Creates an immutable, string-keyed copy of a map tree.
     *
     * @param source source map
     * @return immutable map
     */
    public static Map<String, Object> immutableMap(Map<?, ?> source) {
        Objects.requireNonNull(source, "source");
        Map<String, Object> copy = new LinkedHashMap<>();
        source.forEach((key, value) -> {
            if (key == null) throw new IllegalArgumentException("YAML keys cannot be null");
            copy.put(String.valueOf(key), immutable(value));
        });
        return Collections.unmodifiableMap(copy);
    }

    /**
     * Deep-copies a YAML-compatible value into immutable maps and lists.
     *
     * @param value source value
     * @return immutable value
     */
    @SuppressWarnings("IfCanBeSwitch")
    public static Object immutable(Object value) {
        if (value == null || value instanceof String || value instanceof Number
                || value instanceof Boolean || value instanceof Character || value instanceof Enum<?>) {
            return value instanceof Enum<?> enumeration ? enumeration.name() : value;
        }
        if (value instanceof ConfigurationSerializable serializable) {
            Map<String, Object> serialized = immutableMap(serializable.serialize());
            if (serialized.containsKey(ConfigurationSerialization.SERIALIZED_TYPE_KEY)) return serialized;
            Class<? extends ConfigurationSerializable> serializedType = serializable.getClass()
                    .asSubclass(ConfigurationSerializable.class);
            String alias = ConfigurationSerialization.getAlias(serializedType);
            if (alias == null || alias.isBlank()) alias = serializedType.getName();
            Map<String, Object> withType = new LinkedHashMap<>();
            withType.put(ConfigurationSerialization.SERIALIZED_TYPE_KEY, alias);
            withType.putAll(serialized);
            return Collections.unmodifiableMap(withType);
        }
        if (value instanceof Map<?, ?> map) return immutableMap(map);
        if (value instanceof Iterable<?> iterable) {
            List<Object> copy = new ArrayList<>();
            iterable.forEach(entry -> copy.add(immutable(entry)));
            return Collections.unmodifiableList(copy);
        }
        if (value.getClass().isArray()) {
            int length = Array.getLength(value);
            List<Object> copy = new ArrayList<>(length);
            for (int index = 0; index < length; index++) {
                copy.add(immutable(Array.get(value, index)));
            }
            return Collections.unmodifiableList(copy);
        }
        return String.valueOf(value);
    }

    /**
     * Creates a mutable deep copy suitable for Bukkit YAML APIs.
     *
     * @param value immutable or mutable YAML value
     * @return mutable value
     */
    public static Object mutable(Object value) {
        if (value instanceof Map<?, ?> map) {
            Map<String, Object> copy = new LinkedHashMap<>();
            map.forEach((key, child) -> copy.put(String.valueOf(key), mutable(child)));
            return copy;
        }
        if (value instanceof Iterable<?> iterable) {
            List<Object> copy = new ArrayList<>();
            iterable.forEach(entry -> copy.add(mutable(entry)));
            return copy;
        }
        return value;
    }

    /**
     * Converts an arbitrary map-like tree to a mutable string-keyed map.
     *
     * @param value source value
     * @return mutable map
     */
    public static Map<String, Object> mutableMap(Object value) {
        if (!(value instanceof Map<?, ?> map)) {
            throw new IllegalArgumentException("Expected a YAML object");
        }
        @SuppressWarnings("unchecked")
        Map<String, Object> result = (Map<String, Object>) mutable(map);
        return result;
    }
}
