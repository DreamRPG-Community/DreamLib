package cn.mythicland.dreamlib.material;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class MaterialCatalogTest {

    @Test
    void bundledCatalogContainsLegacyDataVariantsAndChineseNames() {
        MaterialCatalog catalog = MaterialCatalog.bundled();

        assertTrue(catalog.entries().size() > 600);
        assertEquals("青金石", catalog.displayName("INK_SACK", 4));
        assertEquals("青金石矿石", catalog.displayName("LAPIS_ORE", 0));
        assertFalse(catalog.find("INK_SACK", 99).isPresent());
    }

    @Test
    void searchUsesChineseNamesAcrossDataVariants() {
        List<MaterialEntry> matches = MaterialCatalog.bundled().search("青金石");

        assertTrue(matches.stream().anyMatch(entry -> entry.materialName().equals("INK_SACK")
                && entry.data() == 4));
        assertTrue(matches.stream().anyMatch(entry -> entry.materialName().equals("LAPIS_ORE")));
    }
}
