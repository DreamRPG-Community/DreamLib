package cn.mythicland.dreamlib.text;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class LegacyTextTest {

    @Test
    void translatesLegacyColoursAndPreservesLineOrder() {
        assertEquals("§aGreen", LegacyText.colorize("&aGreen"));
        assertEquals("§b§lDreamRPG", LegacyText.colorize("&b&lDreamRPG"));
        assertEquals("Green", LegacyText.stripColor("&aGreen"));
        assertEquals(List.of("§cRed", "§bBlue"), LegacyText.colorize(List.of("&cRed", "&bBlue")));
    }
}
