package cn.mythicland.dreamlib.item;

import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ItemMarkerTest {

    @Test
    void markerCopiesValuesAndRejectsMutationThroughSourceMap() {
        Map<String, String> source = new HashMap<>();
        source.put("item-id", "example");
        ItemMarker marker = new ItemMarker("Example", 1, source);
        source.put("item-id", "changed");

        assertEquals("example", marker.values().get("item-id"));
        assertThrows(UnsupportedOperationException.class, () -> marker.values().put("revision", "x"));
    }

    @Test
    void markerRejectsInvalidSchemaAndNamespace() {
        assertThrows(IllegalArgumentException.class, () -> new ItemMarker("Example", 0, Map.of()));
        assertThrows(IllegalArgumentException.class, () -> new ItemMarker(" ", 1, Map.of()));
        assertThrows(IllegalArgumentException.class, () -> new ItemMarker("Example", 1, Map.of("schema", "bad")));
    }

    @Test
    void reflectiveCodecFailsExplicitlyWithoutABukkitServer() {
        ReflectiveItemMarkerCodec codec = new ReflectiveItemMarkerCodec(java.util.logging.Logger.getAnonymousLogger());
        assertThrows(IllegalStateException.class, () -> codec.read(new ItemStack(Material.STONE), "Example"));
    }
}
