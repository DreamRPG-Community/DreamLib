package cn.mythicland.dreamlib.config;

import org.bukkit.configuration.file.FileConfiguration;

import java.lang.reflect.Constructor;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.RecordComponent;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.function.Consumer;

/**
 * Reflection-only binder for Lib's small annotation-driven configuration models.
 */
final class ConfigBinder {

    private ConfigBinder() {
    }

    static <T> T bind(
            FileConfiguration configuration,
            Consumer<String> warningConsumer,
            Class<T> type
    ) {
        Objects.requireNonNull(configuration, "configuration");
        Objects.requireNonNull(warningConsumer, "warningConsumer");
        Objects.requireNonNull(type, "type");
        if (!type.isRecord()) {
            throw new IllegalArgumentException("Config binding requires a record type: " + type.getName());
        }

        RecordComponent[] components = type.getRecordComponents();
        Class<?>[] parameterTypes = new Class<?>[components.length];
        Object[] arguments = new Object[components.length];
        for (int index = 0; index < components.length; index++) {
            RecordComponent component = components[index];
            parameterTypes[index] = component.getType();
            ConfigValue metadata = component.getAnnotation(ConfigValue.class);
            if (metadata == null) {
                throw new IllegalArgumentException(
                        "Missing @ConfigValue on " + type.getName() + "." + component.getName()
                );
            }
            arguments[index] = read(configuration, warningConsumer, component, metadata);
        }

        try {
            Constructor<T> constructor = type.getDeclaredConstructor(parameterTypes);
            if (!constructor.trySetAccessible()) {
                throw new IllegalStateException("Cannot access configuration record: " + type.getName());
            }
            return constructor.newInstance(arguments);
        } catch (ReflectiveOperationException exception) {
            throw new IllegalStateException("Could not construct configuration record: " + type.getName(), exception);
        }
    }

    private static Object read(
            FileConfiguration configuration,
            Consumer<String> warningConsumer,
            RecordComponent component,
            ConfigValue metadata
    ) {
        Object rawValue = configuration.get(metadata.path());
        if (rawValue == null) rawValue = metadata.defaultValue();
        try {
            return convert(rawValue, component.getType(), component.getGenericType(), metadata);
        } catch (IllegalArgumentException exception) {
            warningConsumer.accept(
                    "Invalid configuration '" + metadata.path() + "' for "
                            + component.getDeclaringRecord().getSimpleName() + "." + component.getName()
                            + ": " + exception.getMessage() + "; using the declared default."
            );
            try {
                return convert(
                        metadata.defaultValue(),
                        component.getType(),
                        component.getGenericType(),
                        metadata
                );
            } catch (IllegalArgumentException defaultException) {
                throw new IllegalStateException(
                        "Invalid declared default for configuration '" + metadata.path() + "'.",
                        defaultException
                );
            }
        }
    }

    private static Object convert(
            Object rawValue,
            Class<?> type,
            Type genericType,
            ConfigValue metadata
    ) {
        if (type == List.class) return listValue(rawValue, genericType, metadata);
        if (type == String.class) return stringValue(rawValue, metadata);
        if (type == boolean.class || type == Boolean.class) return booleanValue(rawValue);
        if (type == byte.class || type == Byte.class) {
            return (byte) integralValue(rawValue, metadata, Byte.MIN_VALUE, Byte.MAX_VALUE);
        }
        if (type == short.class || type == Short.class) {
            return (short) integralValue(rawValue, metadata, Short.MIN_VALUE, Short.MAX_VALUE);
        }
        if (type == int.class || type == Integer.class) {
            return (int) integralValue(rawValue, metadata, Integer.MIN_VALUE, Integer.MAX_VALUE);
        }
        if (type == long.class || type == Long.class) {
            return integralValue(rawValue, metadata, Long.MIN_VALUE, Long.MAX_VALUE);
        }
        if (type == float.class || type == Float.class) {
            double value = numberValue(rawValue, metadata).doubleValue();
            return (float) value;
        }
        if (type == double.class || type == Double.class) return numberValue(rawValue, metadata).doubleValue();
        if (type.isEnum()) return enumValue(rawValue, type);
        throw new IllegalArgumentException("unsupported configuration type: " + type.getName());
    }

    private static List<?> listValue(Object rawValue, Type genericType, ConfigValue metadata) {
        if (!(genericType instanceof ParameterizedType parameterized)
                || !(parameterized.getActualTypeArguments()[0] instanceof Class<?> elementType)) {
            throw new IllegalArgumentException("expected a parameterized List");
        }

        List<Object> rawValues = new ArrayList<>();
        if (rawValue instanceof Iterable<?> values) {
            for (Object value : values) rawValues.add(value);
        } else if (rawValue instanceof String value) {
            String trimmed = value.trim();
            if (!trimmed.isEmpty()) {
                for (String item : trimmed.split(",", -1)) rawValues.add(item.trim());
            }
        } else {
            rawValues.add(rawValue);
        }

        List<Object> converted = new ArrayList<>(rawValues.size());
        for (Object value : rawValues) {
            converted.add(convert(value, elementType, elementType, metadata));
        }
        return List.copyOf(converted);
    }

    private static String stringValue(Object rawValue, ConfigValue metadata) {
        if (!(rawValue instanceof String value)) throw new IllegalArgumentException("expected a string");
        String normalized = metadata.trim() ? value.trim() : value;
        if (metadata.nonBlank() && normalized.isBlank()) {
            throw new IllegalArgumentException("expected a non-blank string");
        }
        return normalized;
    }

    private static boolean booleanValue(Object rawValue) {
        if (rawValue instanceof Boolean value) return value;
        if (rawValue instanceof String value && (value.equalsIgnoreCase("true") || value.equalsIgnoreCase("false"))) {
            return Boolean.parseBoolean(value);
        }
        throw new IllegalArgumentException("expected true or false");
    }

    private static Number numberValue(Object rawValue, ConfigValue metadata) {
        Number number;
        if (rawValue instanceof Number value) number = value;
        else if (rawValue instanceof String value) {
            try {
                number = Double.parseDouble(value.trim());
            } catch (NumberFormatException exception) {
                throw new IllegalArgumentException("expected a number", exception);
            }
        } else {
            throw new IllegalArgumentException("expected a number");
        }

        double value = number.doubleValue();
        if (metadata.finite() && !Double.isFinite(value)) {
            throw new IllegalArgumentException("expected a finite number");
        }
        if (metadata.positive() && value <= 0.0D) throw new IllegalArgumentException("expected a positive number");
        if (metadata.nonNegative() && value < 0.0D) {
            throw new IllegalArgumentException("expected a non-negative number");
        }
        return number;
    }

    private static long integralValue(
            Object rawValue,
            ConfigValue metadata,
            long minimum,
            long maximum
    ) {
        Number number = numberValue(rawValue, metadata);
        double value = number.doubleValue();
        if (value != Math.rint(value)) throw new IllegalArgumentException("expected a whole number");
        if (value < minimum || value > maximum) {
            throw new IllegalArgumentException("number is outside the supported range");
        }
        return number.longValue();
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static Object enumValue(Object rawValue, Class<?> type) {
        if (!(rawValue instanceof String value)) throw new IllegalArgumentException("expected an enum name");
        try {
            return Enum.valueOf((Class<? extends Enum>) type, value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("unknown enum value: " + value, exception);
        }
    }
}
