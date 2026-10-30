package cn.mythicland.dreamlib.material;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class MaterialIconCatalogTest {

    @Test
    void resolvesLegacyDataTextureBeforeGenericFallbacks() {
        MaterialIconCatalog catalog = new MaterialIconCatalog();

        List<String> icons = catalog.iconUrls("WOOL", 14);

        assertEquals(
                "https://assets.mcasset.cloud/1.12.2/assets/minecraft/textures/blocks/wool_colored_red.png",
                icons.getFirst()
        );
        assertThrows(UnsupportedOperationException.class, icons::clear);
    }

    @Test
    void providesItemAndBlockFallbackCandidates() {
        MaterialIconCatalog catalog = new MaterialIconCatalog();

        List<String> icons = catalog.iconUrls("DIAMOND_SWORD", 0);

        assertTrue(icons.stream().anyMatch(value -> value.endsWith("items/diamond_sword.png")));
        assertTrue(catalog.iconUrl("STONE", 0).endsWith("blocks/stone.png"));
    }
}
