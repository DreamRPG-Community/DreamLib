package cn.mythicland.dreamlib.storage;

import org.bukkit.configuration.serialization.ConfigurationSerializable;
import org.bukkit.configuration.serialization.SerializableAs;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class YamlTreeTest {

    @Test
    void preservesConfigurationSerializationTypeAliases() {
        Map<?, ?> copy = assertInstanceOf(Map.class, YamlTree.immutable(new TestValue("lore")));

        assertEquals("YamlTreeTestValue", copy.get("=="));
        assertEquals("lore", copy.get("value"));
        assertThrows(UnsupportedOperationException.class, copy::clear);
    }

    @SerializableAs("YamlTreeTestValue")
    @SuppressWarnings("SameParameterValue")
    private record TestValue(String value) implements ConfigurationSerializable {

        @Override
        public Map<String, Object> serialize() {
            return Map.of("value", value);
        }
    }
}
