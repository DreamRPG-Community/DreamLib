package cn.mythicland.dreamlib.item;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class LoreAttributeParserTest {

    @Test
    void parsesLegacyFormattingAndRanges() {
        LoreAttributeParseResult result = LoreAttributeParser.parseLine("&l§b伤 害： §e+400 - §f450");

        assertTrue(result.isValid());
        LoreAttributeLine attribute = result.attribute().orElseThrow();
        assertEquals("伤 害", attribute.label());
        assertEquals("§b", attribute.labelColor());
        assertEquals(400.0D, attribute.value().minimum());
        assertEquals(450.0D, attribute.value().maximum());
        assertFalse(attribute.value().percent());
    }

    @Test
    void parsesPercentageAndIgnoresDisplayOnlyLines() {
        List<LoreAttributeLine> attributes = LoreAttributeParser.parse(List.of(
                "§7装备描述",
                "&c暴击几率: &e+30%",
                "§f背景故事"
        ));

        assertEquals(1, attributes.size());
        assertEquals("暴击几率", attributes.getFirst().label());
        assertEquals("§c", attributes.getFirst().labelColor());
        assertTrue(attributes.getFirst().value().percent());
    }

    @Test
    void acceptsBothEnglishAndChineseColons() {
        assertTrue(LoreAttributeParser.parseLine("§b伤害: §2+10").isValid());
        assertTrue(LoreAttributeParser.parseLine("§b伤害： §2+10").isValid());
    }

    @Test
    void keepsDetectedLabelForInvalidNumericValue() {
        LoreAttributeParseResult result = LoreAttributeParser.parseLine("防御: 很高");

        assertEquals(LoreAttributeParseResult.Status.INVALID, result.status());
        assertEquals("防御", result.label().orElseThrow());
        assertTrue(result.error().orElseThrow().contains("number")
                || result.error().orElseThrow().contains("数字")
                || result.error().orElseThrow().contains("number"));
    }
}
